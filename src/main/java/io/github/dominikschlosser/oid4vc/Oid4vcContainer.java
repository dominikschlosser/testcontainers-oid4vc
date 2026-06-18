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
package io.github.dominikschlosser.oid4vc;

import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Oid4vcContainer extends GenericContainer<Oid4vcContainer> {

    private static final String DEFAULT_IMAGE = "ghcr.io/dominikschlosser/oid4vc-dev:v1.7.0";
    private static final int WALLET_PORT = 8085;
    private static final int ISSUER_TLS_PORT = 8086;
    private static final Pattern CERTIFICATE_PEM_PATTERN = Pattern.compile(
            "-----BEGIN CERTIFICATE-----\\R.+?\\R-----END CERTIFICATE-----",
            Pattern.DOTALL
    );

    private boolean includeDefaultPid = true;
    private boolean autoAccept = true;
    private boolean statusList = false;
    private boolean requireEncryptedRequest = false;
    private String baseUrl;
    private CredentialFormat preferredFormat;
    private String sessionTranscript;
    private PidClaims customPidClaims;
    private String customPidJson;
    private WalletClient cachedClient;

    public Oid4vcContainer() {
        this(DockerImageName.parse(DEFAULT_IMAGE));
    }

    public Oid4vcContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public Oid4vcContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        addExposedPort(WALLET_PORT);
        addExposedPort(ISSUER_TLS_PORT);
        waitingFor(Wait.forHttp("/").forPort(WALLET_PORT));
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("oid4vp-dev")));
    }

    public Oid4vcContainer withoutDefaultPid() {
        this.includeDefaultPid = false;
        return this;
    }

    public Oid4vcContainer withPidClaims(PidClaims claims) {
        this.customPidClaims = claims;
        return this;
    }

    public Oid4vcContainer withPidClaims(String json) {
        this.customPidJson = json;
        return this;
    }

    public Oid4vcContainer withoutAutoAccept() {
        this.autoAccept = false;
        return this;
    }

    public Oid4vcContainer withStatusList() {
        this.statusList = true;
        return this;
    }

    /**
     * Sets the wallet base URL advertised by oid4vc-dev for its HTTP endpoints.
     * The same host is also reused for the wallet's HTTPS endpoints.
     */
    public Oid4vcContainer withBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    /**
     * Compatibility alias for the older status-list-specific naming.
     */
    public Oid4vcContainer withStatusListBaseUrl(String baseUrl) {
        return withBaseUrl(baseUrl);
    }

    public Oid4vcContainer withPreferredFormat(CredentialFormat format) {
        this.preferredFormat = format;
        return this;
    }

    public Oid4vcContainer withSessionTranscript(String mode) {
        this.sessionTranscript = mode;
        return this;
    }

    /**
     * Requires verifiers to encrypt OID4VP request objects. When enabled, the
     * wallet advertises an encryption key in its {@code wallet_metadata} so that
     * verifiers can encrypt the Authorization Request using JWE.
     */
    public Oid4vcContainer withRequireEncryptedRequest() {
        this.requireEncryptedRequest = true;
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
    public Oid4vcContainer withHostAccess() {
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
        parts.add("oid4vc-dev wallet generate-pid --claims '" + shellEscape(claimsJson) + "'");

        StringBuilder serveCmd = new StringBuilder("oid4vc-dev wallet serve");
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
        List<String> command = new ArrayList<>(List.of(
                "oid4vc-dev",
                "wallet",
                "tls-cert"
        ));
        if (baseUrl != null) {
            command.add("--base-url");
            command.add(baseUrl);
        }
        command.add("--port");
        command.add(String.valueOf(WALLET_PORT));
        return execPemCommand(command, "wallet TLS certificate");
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
        return execPemCommand(
                List.of("oid4vc-dev", "wallet", "ca-cert"),
                "wallet TLS CA certificate"
        );
    }

    /**
     * Writes the PEM-encoded shared wallet CA certificate to the given host path.
     */
    public Path exportWalletTlsCaCertificate(Path outputPath) {
        return writePem(outputPath, getWalletTlsCaCertificatePem(), "wallet TLS CA certificate");
    }

    /**
     * Compatibility alias for callers using the v1.5.0 issuer-focused naming.
     */
    public String getIssuerTlsCertificatePem() {
        return getWalletTlsCertificatePem();
    }

    /**
     * Compatibility alias for callers using the v1.5.0 issuer-focused naming.
     */
    public Path exportIssuerTlsCertificate(Path outputPath) {
        return exportWalletTlsCertificate(outputPath);
    }

    /**
     * Compatibility alias for callers using issuer-focused naming for the
     * shared wallet CA certificate.
     */
    public String getIssuerTlsCaCertificatePem() {
        return getWalletTlsCaCertificatePem();
    }

    /**
     * Compatibility alias for callers using issuer-focused naming for the
     * shared wallet CA certificate.
     */
    public Path exportIssuerTlsCaCertificate(Path outputPath) {
        return exportWalletTlsCaCertificate(outputPath);
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

    private String execPemCommand(List<String> command, String description) {
        try {
            Container.ExecResult result = execInContainer(command.toArray(new String[0]));
            if (result.getExitCode() != 0) {
                throw new WalletClientException("Failed to export " + description + ": " + result.getStderr());
            }
            return normalizeCertificatePem(result.getStdout(), description);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WalletClientException("Failed to export " + description, e);
        } catch (IOException e) {
            throw new WalletClientException("Failed to export " + description, e);
        }
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

    private static String normalizeCertificatePem(String stdout, String description) {
        String output = stdout.strip();
        Matcher matcher = CERTIFICATE_PEM_PATTERN.matcher(output);
        StringBuilder pem = new StringBuilder();
        while (matcher.find()) {
            if (!pem.isEmpty()) {
                pem.append(System.lineSeparator());
            }
            pem.append(matcher.group().strip());
        }
        if (!pem.isEmpty()) {
            return pem.toString();
        }
        throw new WalletClientException("Failed to export " + description + ": no PEM certificate found in command output");
    }
}
