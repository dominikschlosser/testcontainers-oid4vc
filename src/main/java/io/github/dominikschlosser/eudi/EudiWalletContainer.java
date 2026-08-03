/*
 * Copyright 2026 Dominik Schlosser
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.dominikschlosser.eudi;

import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Testcontainers wrapper for the <a href="https://github.com/dominikschlosser/eudi-dev">eudi-dev</a>
 * wallet (formerly oid4vc-dev), providing a containerized EUDI test wallet for
 * OID4VCI and OID4VP integration tests.
 */
public class EudiWalletContainer extends GenericContainer<EudiWalletContainer> {

    private static final String DEFAULT_IMAGE = "ghcr.io/dominikschlosser/eudi-dev:v1.16.1";
    private static final int WALLET_PORT = 8085;
    private static final int ISSUER_TLS_PORT = 8086;
    private static final String CONTAINER_TEMPLATES_DIR = "/templates";

    private boolean includeDefaultPid = true;
    private boolean autoAccept = true;
    private boolean statusList = false;
    private boolean requireEncryptedRequest = false;
    private boolean haip = false;
    private boolean strictValidation = false;
    private String baseUrl;
    private CredentialFormat preferredFormat;
    private String sessionTranscript;
    private String vciClientId;
    private String vciRedirectUri;
    private boolean templatesDirectoryMounted = false;
    private PidClaims customPidClaims;
    private String customPidJson;
    private WalletClient cachedClient;

    public EudiWalletContainer() {
        this(DockerImageName.parse(DEFAULT_IMAGE));
    }

    public EudiWalletContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public EudiWalletContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        addExposedPort(WALLET_PORT);
        addExposedPort(ISSUER_TLS_PORT);
        waitingFor(Wait.forHttp("/api/version").forPort(WALLET_PORT));
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("eudi-dev")));
    }

    public EudiWalletContainer withoutDefaultPid() {
        this.includeDefaultPid = false;
        return this;
    }

    public EudiWalletContainer withPidClaims(PidClaims claims) {
        this.customPidClaims = claims;
        return this;
    }

    public EudiWalletContainer withPidClaims(String json) {
        this.customPidJson = json;
        return this;
    }

    public EudiWalletContainer withoutAutoAccept() {
        this.autoAccept = false;
        return this;
    }

    public EudiWalletContainer withStatusList() {
        this.statusList = true;
        return this;
    }

    /**
     * Sets the wallet base URL advertised by eudi-dev for its HTTP endpoints.
     * The same host is also reused for the wallet's HTTPS endpoints.
     */
    public EudiWalletContainer withBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public EudiWalletContainer withPreferredFormat(CredentialFormat format) {
        this.preferredFormat = format;
        return this;
    }

    public EudiWalletContainer withSessionTranscript(String mode) {
        this.sessionTranscript = mode;
        return this;
    }

    /**
     * Requires verifiers to encrypt OID4VP request objects. When enabled, the
     * wallet advertises an encryption key in its {@code wallet_metadata} so that
     * verifiers can encrypt the Authorization Request using JWE.
     */
    public EudiWalletContainer withRequireEncryptedRequest() {
        this.requireEncryptedRequest = true;
        return this;
    }

    /**
     * Enforces HAIP 1.0 compliance (x509_hash client id prefix,
     * {@code direct_post.jwt}, DCQL, JAR, ES256).
     */
    public EudiWalletContainer withHaip() {
        this.haip = true;
        return this;
    }

    /**
     * Runs the wallet in {@code strict} validation mode instead of the lenient
     * {@code debug} default, so spec violations by the issuer or verifier under
     * test fail the flow instead of being logged and tolerated.
     */
    public EudiWalletContainer withStrictValidation() {
        this.strictValidation = true;
        return this;
    }

    /**
     * Client ID the wallet should use for OID4VCI authorization-code flows.
     */
    public EudiWalletContainer withVciClientId(String clientId) {
        this.vciClientId = clientId;
        return this;
    }

    /**
     * Redirect URI the wallet should use for OID4VCI authorization-code flows.
     */
    public EudiWalletContainer withVciRedirectUri(String redirectUri) {
        this.vciRedirectUri = redirectUri;
        return this;
    }

    /**
     * Copies a host directory of credential template JSON files into the
     * container and points the wallet at it via {@code --templates-dir}, so a
     * project folder of shared templates works as a self-contained setup.
     * Templates are also manageable at runtime via
     * {@link WalletClient#saveTemplate(CredentialTemplate)}.
     */
    public EudiWalletContainer withTemplatesDirectory(Path hostDirectory) {
        withCopyFileToContainer(
                MountableFile.forHostPath(hostDirectory),
                CONTAINER_TEMPLATES_DIR
        );
        this.templatesDirectoryMounted = true;
        return this;
    }

    /**
     * Maps {@code localhost} inside the container to the Docker host via the
     * {@code host-gateway} special address. This allows the wallet to reach
     * services running on the host machine (e.g. an issuer or verifier started
     * in the test) using {@code localhost:<port>}.
     *
     * <p>The {@code host-gateway} value is resolved by the Docker daemon to the
     * host's internal IP address (typically {@code 172.17.0.1} on Linux or
     * the VM gateway on Docker Desktop). It is equivalent to Docker's
     * {@code --add-host=localhost:host-gateway} CLI flag.
     *
     * <p>IPv6 is disabled inside the container so that {@code localhost} resolves
     * over IPv4 to the mapped host gateway. Without this, on a Linux Docker host
     * the wallet resolves {@code localhost} to its own {@code ::1} loopback first
     * and never reaches the host (the mapping works on Docker Desktop regardless,
     * which is why this only surfaces on Linux/CI).
     *
     * <p>Combine with {@link org.testcontainers.Testcontainers#exposeHostPorts(int...)}
     * to make specific host ports accessible.
     */
    public EudiWalletContainer withHostAccess() {
        withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                .withSysctls(Map.of(
                        "net.ipv6.conf.all.disable_ipv6", "1",
                        "net.ipv6.conf.default.disable_ipv6", "1")));
        return withExtraHost("localhost", "host-gateway");
    }

    @Override
    protected void configure() {
        String claimsJson = resolveCustomPidJson();

        if (claimsJson != null) {
            configureWithCustomPid(claimsJson);
        } else {
            configureStandard();
        }
    }

    private List<String> buildServeFlags() {
        List<String> flags = new ArrayList<>();
        flags.add("--port");
        flags.add(String.valueOf(WALLET_PORT));
        if (autoAccept) {
            flags.add("--auto-accept");
        }
        if (statusList) {
            flags.add("--status-list");
        }
        if (baseUrl != null) {
            flags.add("--base-url");
            flags.add(baseUrl);
        }
        if (preferredFormat != null) {
            flags.add("--preferred-format");
            flags.add(preferredFormat.getWireValue());
        }
        if (sessionTranscript != null) {
            flags.add("--session-transcript");
            flags.add(sessionTranscript);
        }
        if (requireEncryptedRequest) {
            flags.add("--require-encrypted-request");
        }
        if (haip) {
            flags.add("--haip");
        }
        if (strictValidation) {
            flags.add("--mode");
            flags.add("strict");
        }
        if (vciClientId != null) {
            flags.add("--vci-client-id");
            flags.add(vciClientId);
        }
        if (vciRedirectUri != null) {
            flags.add("--vci-redirect-uri");
            flags.add(vciRedirectUri);
        }
        if (templatesDirectoryMounted) {
            flags.add("--templates-dir");
            flags.add(CONTAINER_TEMPLATES_DIR);
        }
        return flags;
    }

    private void configureStandard() {
        List<String> cmd = new ArrayList<>();
        cmd.add("wallet");
        cmd.add("serve");
        cmd.addAll(buildServeFlags());
        if (includeDefaultPid) {
            cmd.add("--pid");
        }
        setCommand(cmd.toArray(new String[0]));
    }

    private void configureWithCustomPid(String claimsJson) {
        // generate-pid replaces any existing PID credentials, so --pid on serve
        // is unnecessary — the custom claims fully define the PID content.
        List<String> parts = new ArrayList<>();
        parts.add("eudi wallet generate-pid --claims '" + shellEscape(claimsJson) + "'");

        StringBuilder serveCmd = new StringBuilder("eudi wallet serve");
        for (String flag : buildServeFlags()) {
            serveCmd.append(" ").append(flag);
        }
        parts.add(serveCmd.toString());

        withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("sh", "-c"));
        setCommand(new String[]{String.join(" && ", parts)});
    }

    public String getBaseUrl() {
        return "http://" + getHost() + ":" + getMappedPort(WALLET_PORT);
    }

    public String getHttpsBaseUrl() {
        return "https://" + getHost() + ":" + getMappedPort(ISSUER_TLS_PORT);
    }

    public String getAuthorizeUrl() {
        return getBaseUrl() + "/authorize";
    }

    public String getHttpsAuthorizeUrl() {
        return getHttpsBaseUrl() + "/authorize";
    }

    /**
     * The wallet's credential offer endpoint. Deliver an offer to
     * {@code <url>?credential_offer_uri=...} (or {@code credential_offer=...})
     * instead of the {@code openid-credential-offer://} custom scheme.
     */
    public String getCredentialOfferUrl() {
        return getBaseUrl() + "/credential-offer";
    }

    public String getHttpsCredentialOfferUrl() {
        return getHttpsBaseUrl() + "/credential-offer";
    }

    public String getTrustListUrl() {
        return getBaseUrl() + "/api/trustlist";
    }

    public String getTrustListUrl(String id) {
        return getTrustListsUrl() + "/" + urlEncode(id);
    }

    public String getTrustListUrlForVct(String vct) {
        return getTrustListUrl() + "?vct=" + urlEncode(vct);
    }

    public String getTrustListUrlForDocType(String docType) {
        return getTrustListUrl() + "?doctype=" + urlEncode(docType);
    }

    public String getHttpsTrustListUrl() {
        return getHttpsBaseUrl() + "/api/trustlist";
    }

    public String getHttpsTrustListUrl(String id) {
        return getHttpsTrustListsUrl() + "/" + urlEncode(id);
    }

    public String getHttpsTrustListUrlForVct(String vct) {
        return getHttpsTrustListUrl() + "?vct=" + urlEncode(vct);
    }

    public String getHttpsTrustListUrlForDocType(String docType) {
        return getHttpsTrustListUrl() + "?doctype=" + urlEncode(docType);
    }

    public String getTrustListsUrl() {
        return getBaseUrl() + "/api/trustlists";
    }

    public String getHttpsTrustListsUrl() {
        return getHttpsBaseUrl() + "/api/trustlists";
    }

    /**
     * Resolves a trust-list discovery entry against this container's mapped
     * HTTP wallet address. Prefer this over {@code entry.url()} when the wallet
     * runs behind Docker port mappings or Testcontainers.
     */
    public String resolveTrustListUrl(TrustListIndexEntry entry) {
        return entry.resolveUrl(getBaseUrl());
    }

    /**
     * Resolves a trust-list discovery entry against this container's mapped
     * HTTPS wallet address.
     */
    public String resolveHttpsTrustListUrl(TrustListIndexEntry entry) {
        return entry.resolveUrl(getHttpsBaseUrl());
    }

    public String getIssuerUrl() {
        return getHttpsBaseUrl();
    }

    public String getIssuerMetadataUrl() {
        return getHttpsBaseUrl() + "/.well-known/jwt-vc-issuer";
    }

    public String getCredentialsUrl() {
        return getBaseUrl() + "/api/credentials";
    }

    public String getTemplatesUrl() {
        return getBaseUrl() + "/api/templates";
    }

    public String getStatusListUrl() {
        return getBaseUrl() + "/api/statuslist";
    }

    public String getHttpsStatusListUrl() {
        return getHttpsBaseUrl() + "/api/statuslist";
    }

    public OfferResponse acceptCredentialOffer(String uri) {
        return client().acceptCredentialOffer(uri);
    }

    public PresentationResponse acceptPresentationRequest(String uri) {
        return client().acceptPresentationRequest(uri);
    }

    public List<Credential> listCredentials() {
        return client().getCredentials();
    }

    public WalletClient client() {
        if (cachedClient == null) {
            cachedClient = new WalletClient(getBaseUrl());
        }
        return cachedClient;
    }

    /**
     * Returns the PEM-encoded leaf HTTPS certificate used by this wallet's
     * HTTPS endpoints. Tests can add this certificate to a trust store to call
     * this wallet's metadata, trust list, or status list endpoints without
     * disabling TLS verification.
     *
     * <p>If multiple wallet HTTPS certificates are issued by a shared CA,
     * prefer {@link #getWalletTlsCaCertificatePem()} for trust stores that
     * must validate sibling wallet certificates as well.
     */
    public String getWalletTlsCertificatePem() {
        return client().getTlsCertificatePem();
    }

    /**
     * Returns the wallet HTTPS leaf certificate as a JWKS document (public key
     * with {@code x5c} chain), e.g. for pasting into Keycloak trust
     * configuration.
     */
    public String getWalletTlsCertificateJwks() {
        return client().getTlsCertificateJwks();
    }

    /**
     * Writes the PEM-encoded wallet HTTPS leaf certificate to the given host path.
     */
    public Path exportWalletTlsCertificate(Path outputPath) {
        return writePem(outputPath, getWalletTlsCertificatePem(), "wallet TLS certificate");
    }

    /**
     * Returns the PEM-encoded shared CA certificate used to sign wallet HTTPS
     * certificates and related x5c chains.
     */
    public String getWalletTlsCaCertificatePem() {
        return client().getCaCertificatePem();
    }

    /**
     * Returns the shared wallet CA certificate as a JWKS document (public key
     * with {@code x5c} chain).
     */
    public String getWalletTlsCaCertificateJwks() {
        return client().getCaCertificateJwks();
    }

    /**
     * Writes the PEM-encoded shared wallet CA certificate to the given host path.
     */
    public Path exportWalletTlsCaCertificate(Path outputPath) {
        return writePem(outputPath, getWalletTlsCaCertificatePem(), "wallet TLS CA certificate");
    }

    private String resolveCustomPidJson() {
        if (customPidClaims != null) {
            return customPidClaims.toJson();
        }
        return customPidJson;
    }

    private static String shellEscape(String value) {
        return value.replace("'", "'\\''");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Path writePem(Path outputPath, String pem, String description) {
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, pem + System.lineSeparator());
            return outputPath;
        } catch (IOException e) {
            throw new WalletClientException("Failed to write " + description + " to " + outputPath, e);
        }
    }
}
