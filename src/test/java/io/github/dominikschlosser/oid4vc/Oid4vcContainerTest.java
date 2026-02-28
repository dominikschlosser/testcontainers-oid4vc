package io.github.dominikschlosser.oid4vc;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class Oid4vcContainerTest {

    @Container
    static Oid4vcContainer wallet = new Oid4vcContainer("ghcr.io/dominikschlosser/oid4vc-dev:v0.14.2")
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
        try (Oid4vcContainer customWallet = new Oid4vcContainer("ghcr.io/dominikschlosser/oid4vc-dev:v0.14.2")
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

    @Test
    void hasCredentialWithTypeFindsExistingCredential() {
        WalletClient client = wallet.client();
        List<Credential> credentials = client.getCredentials();
        String existingType = credentials.getFirst().type();

        assertThat(client.hasCredentialWithType(existingType)).isTrue();
    }

    @Test
    void hasCredentialWithTypeReturnsFalseForMissing() {
        assertThat(wallet.client().hasCredentialWithType("urn:nonexistent:type")).isFalse();
    }

    @Test
    void getCredentialsByTypeReturnsMatchingCredentials() {
        WalletClient client = wallet.client();

        String sdJwt1 = new SdJwtCredentialBuilder()
                .vct("urn:test:by-type:1")
                .claim("seq", "first")
                .build();
        String sdJwt2 = new SdJwtCredentialBuilder()
                .vct("urn:test:by-type:1")
                .claim("seq", "second")
                .build();

        client.importCredential(sdJwt1);
        client.importCredential(sdJwt2);

        List<Credential> matched = client.getCredentialsByType("urn:test:by-type:1");
        assertThat(matched).hasSize(2);
        assertThat(matched).allMatch(c -> "urn:test:by-type:1".equals(c.type()));

        // Clean up
        client.deleteCredentialsByType("urn:test:by-type:1");
    }

    @Test
    void getCredentialsByTypeReturnsEmptyListForMissing() {
        assertThat(wallet.client().getCredentialsByType("urn:nonexistent:type")).isEmpty();
    }

    @Test
    void importAndDeleteCredential() {
        WalletClient client = wallet.client();

        String sdJwt = new SdJwtCredentialBuilder()
                .vct("urn:test:import-delete:1")
                .claim("name", "Test")
                .build();

        int countBefore = client.getCredentials().size();
        client.importCredential(sdJwt);

        List<Credential> afterImport = client.getCredentials();
        assertThat(afterImport).hasSize(countBefore + 1);

        Credential imported = afterImport.stream()
                .filter(c -> "urn:test:import-delete:1".equals(c.type()))
                .findFirst()
                .orElseThrow();

        client.deleteCredential(imported.id());
        assertThat(client.getCredentials()).hasSize(countBefore);
    }

    @Test
    void deleteCredentialsByType() {
        WalletClient client = wallet.client();

        String sdJwt1 = new SdJwtCredentialBuilder()
                .vct("urn:test:bulk-delete:1")
                .claim("seq", "first")
                .build();
        String sdJwt2 = new SdJwtCredentialBuilder()
                .vct("urn:test:bulk-delete:1")
                .claim("seq", "second")
                .build();

        client.importCredential(sdJwt1);
        client.importCredential(sdJwt2);
        assertThat(client.hasCredentialWithType("urn:test:bulk-delete:1")).isTrue();

        client.deleteCredentialsByType("urn:test:bulk-delete:1");
        assertThat(client.hasCredentialWithType("urn:test:bulk-delete:1")).isFalse();
    }

    @Test
    void deleteNonExistentCredentialThrows() {
        assertThatThrownBy(() -> wallet.client().deleteCredential("non-existent-id"))
                .isInstanceOf(WalletClientException.class);
    }

    @Test
    void withHostAccessConfiguresContainer() {
        try (Oid4vcContainer hostWallet = new Oid4vcContainer("ghcr.io/dominikschlosser/oid4vc-dev:v0.14.2")
                .withHostAccess()) {
            hostWallet.start();
            assertThat(hostWallet.isRunning()).isTrue();
            // Verify the container started successfully with host-gateway configured
            assertThat(hostWallet.listCredentials()).isNotEmpty();
        }
    }

    @Test
    void importSdJwtCredentialWithObjectAndArrayClaims() {
        WalletClient client = wallet.client();

        String sdJwt = new SdJwtCredentialBuilder()
                .vct("urn:test:complex:1")
                .issuer("https://test.example.com")
                .claim("given_name", "Jane")
                .objectClaim("address", Map.of("city", "Berlin"))
                .arrayClaim("nationalities", List.of("DE", "US"))
                .build();

        client.importCredential(sdJwt);
        assertThat(client.hasCredentialWithType("urn:test:complex:1")).isTrue();

        // Clean up
        client.deleteCredentialsByType("urn:test:complex:1");
    }
}
