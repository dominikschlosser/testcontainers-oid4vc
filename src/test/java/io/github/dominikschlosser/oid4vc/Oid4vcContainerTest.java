package io.github.dominikschlosser.oid4vc;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class Oid4vcContainerTest {

    @Container
    static Oid4vcContainer wallet = new Oid4vcContainer("ghcr.io/dominikschlosser/oid4vc-dev:v0.13.2")
            .withStatusList();

    @Test
    void containerStartsAndIsRunning() {
        assertThat(wallet.isRunning()).isTrue();
    }

    @Test
    void listCredentialsReturnsPreloadedPid() {
        List<Credential> credentials = wallet.listCredentials();

        assertThat(credentials).isNotEmpty();
        assertThat(credentials).anyMatch(c -> c.format() == CredentialFormat.SD_JWT);
    }

    @Test
    void credentialsHaveId() {
        List<Credential> credentials = wallet.listCredentials();

        assertThat(credentials).allMatch(c -> c.id() != null && !c.id().isBlank());
    }

    @Test
    void clientGetCredentialsMatchesListCredentials() {
        WalletClient client = wallet.client();
        List<Credential> credentials = client.getCredentials();

        assertThat(credentials).isNotEmpty();
        assertThat(credentials).isEqualTo(wallet.listCredentials());
    }

    @Test
    void trustListReturnsJwt() {
        String trustList = wallet.client().getTrustList();

        assertThat(trustList).isNotBlank();
        assertThat(trustList.split("\\.")).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void statusListReturnsJwt() {
        String statusList = wallet.client().getStatusList();

        assertThat(statusList).isNotBlank();
        assertThat(statusList.split("\\.")).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void urlAccessorsReturnMappedPort() {
        assertThat(wallet.getBaseUrl()).startsWith("http://");
        assertThat(wallet.getAuthorizeUrl()).endsWith("/authorize");
        assertThat(wallet.getTrustListUrl()).endsWith("/api/trustlist");
        assertThat(wallet.getCredentialsUrl()).endsWith("/api/credentials");
        assertThat(wallet.getStatusListUrl()).endsWith("/api/statuslist");
    }

    @Test
    void customPidClaims() {
        try (Oid4vcContainer customWallet = new Oid4vcContainer("ghcr.io/dominikschlosser/oid4vc-dev:v0.13.2")
                .withPidClaims(new SdJwtPidClaims()
                        .givenName("MAX")
                        .familyName("POWER"))) {
            customWallet.start();

            List<Credential> credentials = customWallet.listCredentials();
            assertThat(credentials).isNotEmpty();
            assertThat(credentials).anyMatch(c ->
                    "MAX".equals(c.claims().get("given_name"))
                            && "POWER".equals(c.claims().get("family_name")));
        }
    }

    @Test
    void setAndClearNextError() {
        WalletClient client = wallet.client();

        // Should not throw
        client.setNextError("access_denied", "User denied consent");
        client.clearNextError();
    }

    @Test
    void setAndClearPreferredFormat() {
        WalletClient client = wallet.client();

        client.setPreferredFormat(CredentialFormat.MSO_MDOC);
        client.setPreferredFormat(CredentialFormat.SD_JWT);
        client.clearPreferredFormat();
    }

    @Test
    void revokeAndUnrevokeCredential() {
        WalletClient client = wallet.client();
        List<Credential> credentials = client.getCredentials();

        String sdJwtId = credentials.stream()
                .filter(c -> c.format() == CredentialFormat.SD_JWT)
                .findFirst()
                .map(Credential::id)
                .orElseThrow();

        client.revokeCredential(sdJwtId);
        client.unrevokeCredential(sdJwtId);
    }
}
