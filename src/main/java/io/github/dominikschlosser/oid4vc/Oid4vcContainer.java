package io.github.dominikschlosser.oid4vc;

import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

public class Oid4vcContainer extends GenericContainer<Oid4vcContainer> {

    private static final String DEFAULT_IMAGE = "ghcr.io/dominikschlosser/oid4vc-dev";
    private static final int WALLET_PORT = 8085;

    private boolean includeDefaultPid = true;
    private boolean autoAccept = true;
    private boolean statusList = false;
    private String statusListBaseUrl;
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

    public Oid4vcContainer withStatusListBaseUrl(String baseUrl) {
        this.statusListBaseUrl = baseUrl;
        return this;
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
     * <p>Combine with {@link org.testcontainers.Testcontainers#exposeHostPorts(int...)}
     * to make specific host ports accessible.
     */
    public Oid4vcContainer withHostAccess() {
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
            if (statusListBaseUrl != null) {
                flags.add("--base-url");
                flags.add(statusListBaseUrl);
            }
        }
        if (preferredFormat != null) {
            flags.add("--preferred-format");
            flags.add(preferredFormat.getWireValue());
        }
        if (sessionTranscript != null) {
            flags.add("--session-transcript");
            flags.add(sessionTranscript);
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

    public String getAuthorizeUrl() {
        return getBaseUrl() + "/authorize";
    }

    public String getTrustListUrl() {
        return getBaseUrl() + "/api/trustlist";
    }

    public String getCredentialsUrl() {
        return getBaseUrl() + "/api/credentials";
    }

    public String getStatusListUrl() {
        return getBaseUrl() + "/api/statuslist";
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

    private String resolveCustomPidJson() {
        if (customPidClaims != null) {
            return customPidClaims.toJson();
        }
        return customPidJson;
    }

    private static String shellEscape(String value) {
        return value.replace("'", "'\\''");
    }
}
