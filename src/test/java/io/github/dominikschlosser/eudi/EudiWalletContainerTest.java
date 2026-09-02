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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.TrustManagerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class EudiWalletContainerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Container
    static EudiWalletContainer wallet = new EudiWalletContainer()
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
    void trustListIndexExposesPidAndLocalProfiles() {
        WalletClient client = wallet.client();
        String vct = "urn:test:trust-list-index:1";
        String sdJwt = new SdJwtCredentialBuilder()
                .vct(vct)
                .claim("name", "Trust List Index")
                .build();

        client.importCredential(sdJwt);
        try {
            List<TrustListIndexEntry> trustLists = client.getTrustLists();

            assertThat(trustLists).extracting(TrustListIndexEntry::id).contains("pid", "local");
            assertThat(trustLists).filteredOn(TrustListIndexEntry::defaultProfile).singleElement()
                    .extracting(TrustListIndexEntry::id)
                    .isEqualTo("pid");
            assertThat(trustLists).filteredOn(entry -> "local".equals(entry.id())).singleElement()
                    .satisfies(entry -> {
                        assertThat(entry.path()).isEqualTo("/api/trustlists/local");
                        assertThat(entry.advertisedUrl()).endsWith("/api/trustlists/local");
                        assertThat(entry.url()).isEqualTo(entry.advertisedUrl());
                        assertThat(entry.resolveUrl(wallet.getTrustListsUrl())).isEqualTo(wallet.getTrustListUrl("local"));
                        assertThat(wallet.resolveTrustListUrl(entry)).isEqualTo(wallet.getTrustListUrl("local"));
                        assertThat(wallet.resolveHttpsTrustListUrl(entry)).isEqualTo(wallet.getHttpsTrustListUrl("local"));
                        assertThat(entry.attestations()).extracting(TrustListAttestation::vct).contains(vct);
                    });
        } finally {
            client.deleteCredentialsByType(vct);
        }
    }

    @Test
    void trustListSelectorsReturnExpectedProfiles() throws Exception {
        WalletClient client = wallet.client();
        String localVct = "urn:test:trust-list-selector:1";
        String sdJwt = new SdJwtCredentialBuilder()
                .vct(localVct)
                .claim("name", "Trust List Selector")
                .build();

        client.importCredential(sdJwt);
        try {
            String defaultTrustList = client.getTrustList();
            String localTrustList = client.getTrustListById("local");
            String localByVctTrustList = client.getTrustListForVct(localVct);

            String pidDocType = client.getCredentials().stream()
                    .filter(c -> c.format() == CredentialFormat.MSO_MDOC)
                    .map(Credential::type)
                    .findFirst()
                    .orElseThrow();
            String pidByDocTypeTrustList = client.getTrustListForDocType(pidDocType);

            assertThat(trustListLoteType(defaultTrustList))
                    .isEqualTo("http://uri.etsi.org/19602/LoTEType/EUPIDProvidersList");
            assertThat(trustListLoteType(localTrustList))
                    .isEqualTo("http://uri.etsi.org/19602/LoTEType/local");
            assertThat(trustListLoteType(localByVctTrustList))
                    .isEqualTo("http://uri.etsi.org/19602/LoTEType/local");
            assertThat(trustListLoteType(pidByDocTypeTrustList))
                    .isEqualTo("http://uri.etsi.org/19602/LoTEType/EUPIDProvidersList");
        } finally {
            client.deleteCredentialsByType(localVct);
        }
    }

    @Test
    void urlAccessorsReturnMappedPort() {
        assertThat(wallet.getBaseUrl()).startsWith("http://");
        assertThat(wallet.getHttpsBaseUrl()).startsWith("https://");
        assertThat(wallet.getAuthorizeUrl()).endsWith("/authorize");
        assertThat(wallet.getHttpsAuthorizeUrl()).endsWith("/authorize");
        assertThat(wallet.getTrustListUrl()).endsWith("/api/trustlist");
        assertThat(wallet.getTrustListsUrl()).endsWith("/api/trustlists");
        assertThat(wallet.getTrustListUrl("local")).endsWith("/api/trustlists/local");
        assertThat(wallet.getTrustListUrlForVct("urn:test:url accessor:1"))
                .endsWith("/api/trustlist?vct=urn%3Atest%3Aurl+accessor%3A1");
        assertThat(wallet.getTrustListUrlForDocType("org.iso.23220.photoid.1"))
                .endsWith("/api/trustlist?doctype=org.iso.23220.photoid.1");
        assertThat(wallet.getHttpsTrustListUrl()).endsWith("/api/trustlist");
        assertThat(wallet.getHttpsTrustListsUrl()).endsWith("/api/trustlists");
        assertThat(wallet.getHttpsTrustListUrl("local")).endsWith("/api/trustlists/local");
        assertThat(wallet.getHttpsTrustListUrlForVct("urn:test:url accessor:1"))
                .endsWith("/api/trustlist?vct=urn%3Atest%3Aurl+accessor%3A1");
        assertThat(wallet.getHttpsTrustListUrlForDocType("org.iso.23220.photoid.1"))
                .endsWith("/api/trustlist?doctype=org.iso.23220.photoid.1");
        assertThat(wallet.getIssuerUrl()).startsWith("https://");
        assertThat(wallet.getIssuerMetadataUrl()).endsWith("/.well-known/jwt-vc-issuer");
        assertThat(wallet.getCredentialsUrl()).endsWith("/api/credentials");
        assertThat(wallet.getCredentialOfferUrl()).endsWith("/credential-offer");
        assertThat(wallet.getHttpsCredentialOfferUrl()).endsWith("/credential-offer");
        assertThat(wallet.getTemplatesUrl()).endsWith("/api/templates");
        assertThat(wallet.getStatusListUrl()).endsWith("/api/statuslist");
        assertThat(wallet.getHttpsStatusListUrl()).endsWith("/api/statuslist");
    }

    @Test
    void customPidClaims() {
        try (EudiWalletContainer customWallet = new EudiWalletContainer()
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
    void germanPidTypeGeneratesGermanPid() {
        try (EudiWalletContainer germanWallet = new EudiWalletContainer()
                .withPidType("urn:eudi:pid:de:1")
                .withPidClaims(new SdJwtPidClaims()
                        .givenName("MAX"))) {
            germanWallet.start();

            List<Credential> credentials = germanWallet.listCredentials();
            assertThat(credentials)
                    .filteredOn(c -> c.format() == CredentialFormat.SD_JWT)
                    .anyMatch(c -> "urn:eudi:pid:de:1".equals(c.type())
                            && "MAX".equals(c.claims().get("given_name")));
            // ISO 18013-5 has no doctype inheritance: the German mdoc PID
            // keeps the country-independent doctype and differs by namespace
            assertThat(credentials)
                    .filteredOn(c -> c.format() == CredentialFormat.MSO_MDOC)
                    .anyMatch(c -> "eu.europa.ec.eudi.pid.1".equals(c.type()));
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
        try (EudiWalletContainer hostWallet = new EudiWalletContainer()
                .withHostAccess()) {
            hostWallet.start();
            assertThat(hostWallet.isRunning()).isTrue();
            // Verify the container started successfully with host-gateway configured
            assertThat(hostWallet.listCredentials()).isNotEmpty();
        }
    }

    @Test
    void withRequireEncryptedRequestStartsSuccessfully() {
        try (EudiWalletContainer encWallet = new EudiWalletContainer()
                .withRequireEncryptedRequest()) {
            encWallet.start();
            assertThat(encWallet.isRunning()).isTrue();
            assertThat(encWallet.listCredentials()).isNotEmpty();
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

    @Test
    void walletTlsCertificatePemCanBeExported() {
        String pem = wallet.getWalletTlsCertificatePem();

        assertThat(pem).startsWith("-----BEGIN CERTIFICATE-----");
        assertThat(pem).contains("-----END CERTIFICATE-----");
    }

    @Test
    void walletTlsCertificateCanBeWrittenToFile() throws Exception {
        Path certificateFile = Files.createTempFile("eudi-wallet", ".pem");

        Path exportedPath = wallet.exportWalletTlsCertificate(certificateFile);

        assertThat(exportedPath).isEqualTo(certificateFile);
        assertThat(Files.readString(certificateFile)).contains("BEGIN CERTIFICATE");
    }

    @Test
    void walletTlsCaCertificatePemCanBeExported() {
        String pem = wallet.getWalletTlsCaCertificatePem();

        assertThat(pem).startsWith("-----BEGIN CERTIFICATE-----");
        assertThat(pem).contains("-----END CERTIFICATE-----");
    }

    @Test
    void walletTlsCaCertificateCanBeWrittenToFile() throws Exception {
        Path certificateFile = Files.createTempFile("eudi-wallet-ca", ".pem");

        Path exportedPath = wallet.exportWalletTlsCaCertificate(certificateFile);

        assertThat(exportedPath).isEqualTo(certificateFile);
        assertThat(Files.readString(certificateFile)).contains("BEGIN CERTIFICATE");
    }

    @Test
    void issuerMetadataEndpointCanBeTrustedWithExportedLeafCertificate() throws Exception {
        SSLContext sslContext = sslContextFromPem(wallet.getWalletTlsCertificatePem());
        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(wallet.getIssuerMetadataUrl())).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"issuer\"");
        assertThat(response.body()).contains("\"jwks\"");
    }

    @Test
    void httpsTrustListAndStatusListCanBeTrustedWithExportedLeafCertificate() throws Exception {
        SSLContext sslContext = sslContextFromPem(wallet.getWalletTlsCertificatePem());
        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();

        HttpResponse<String> trustListResponse = client.send(
                HttpRequest.newBuilder(URI.create(wallet.getHttpsTrustListUrl())).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        HttpResponse<String> statusListResponse = client.send(
                HttpRequest.newBuilder(URI.create(wallet.getHttpsStatusListUrl())).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(trustListResponse.statusCode()).isEqualTo(200);
        assertThat(trustListResponse.body().split("\\.")).hasSizeGreaterThanOrEqualTo(3);
        assertThat(statusListResponse.statusCode()).isEqualTo(200);
        assertThat(statusListResponse.body().split("\\.")).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void httpsEndpointsCanBeTrustedWithExportedCaCertificate() throws Exception {
        SSLContext sslContext = sslContextFromPem(wallet.getWalletTlsCaCertificatePem());
        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();

        HttpResponse<String> issuerMetadataResponse = client.send(
                HttpRequest.newBuilder(URI.create(wallet.getIssuerMetadataUrl())).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        HttpResponse<String> trustListResponse = client.send(
                HttpRequest.newBuilder(URI.create(wallet.getHttpsTrustListUrl())).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        HttpResponse<String> statusListResponse = client.send(
                HttpRequest.newBuilder(URI.create(wallet.getHttpsStatusListUrl())).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(issuerMetadataResponse.statusCode()).isEqualTo(200);
        assertThat(issuerMetadataResponse.body()).contains("\"issuer\"");
        assertThat(trustListResponse.statusCode()).isEqualTo(200);
        assertThat(trustListResponse.body().split("\\.")).hasSizeGreaterThanOrEqualTo(3);
        assertThat(statusListResponse.statusCode()).isEqualTo(200);
        assertThat(statusListResponse.body().split("\\.")).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void certificatesCanBeExportedAsJwks() throws Exception {
        String tlsJwks = wallet.getWalletTlsCertificateJwks();
        String caJwks = wallet.getWalletTlsCaCertificateJwks();

        Map<String, Object> parsedTls = MAPPER.readValue(tlsJwks, new TypeReference<>() {});
        Map<String, Object> parsedCa = MAPPER.readValue(caJwks, new TypeReference<>() {});
        assertThat((List<?>) parsedTls.get("keys")).isNotEmpty();
        assertThat((List<?>) parsedCa.get("keys")).isNotEmpty();
        assertThat(tlsJwks).contains("x5c");
    }

    @Test
    void issueCredentialFromPredefinedTemplate() {
        WalletClient client = wallet.client();

        Credential issued = client.issueCredential(IssueRequest.fromTemplate("german-pid-sdjwt")
                .claim("given_name", "TEMPLATE")
                .claim("family_name", "OVERRIDE"));

        try {
            assertThat(issued.id()).isNotBlank();
            assertThat(issued.format()).isEqualTo(CredentialFormat.SD_JWT);
            assertThat(issued.claims())
                    .containsEntry("given_name", "TEMPLATE")
                    .containsEntry("family_name", "OVERRIDE");
            // untouched template claims stay in place
            assertThat(issued.claims()).containsKey("birthdate");
            assertThat(issued.raw()).isNotBlank();
        } finally {
            client.deleteCredential(issued.id());
        }
    }

    @Test
    void issueFreeFormSdJwtCredential() {
        WalletClient client = wallet.client();

        Credential issued = client.issueCredential(IssueRequest.sdJwt("urn:test:issued:1")
                .claim("given_name", "Jane")
                .claim("family_name", "Doe")
                .alwaysDisclosed("given_name")
                .ttl(java.time.Duration.ofHours(1)));

        try {
            assertThat(issued.type()).isEqualTo("urn:test:issued:1");
            assertThat(client.getCredential(issued.id()).id()).isEqualTo(issued.id());
            assertThat(client.hasCredentialWithType("urn:test:issued:1")).isTrue();
        } finally {
            client.deleteCredential(issued.id());
        }
    }

    @Test
    void issueCredentialWithSigningOverrideEmbedsGivenChain() throws Exception {
        WalletClient client = wallet.client();
        String key = Files.readString(Path.of("src/test/resources/override-issuer-key.pem"));
        String cert = Files.readString(Path.of("src/test/resources/override-issuer-cert.pem"));

        Credential issued = client.issueCredential(IssueRequest.sdJwt("urn:test:override:1")
                .claim("given_name", "Jane")
                .signedBy(key, cert));

        try {
            String headerJson = new String(
                    Base64.getUrlDecoder().decode(issued.raw().split("\\.")[0]),
                    StandardCharsets.UTF_8);
            Map<String, Object> header = MAPPER.readValue(headerJson, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<String> x5c = (List<String>) header.get("x5c");
            String certBase64 = cert
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            assertThat(x5c).containsExactly(certBase64);
        } finally {
            client.deleteCredential(issued.id());
        }
    }

    @Test
    void issuedCredentialCarriesResolvableStatus() {
        WalletClient client = wallet.client();

        // the shared wallet runs with --status-list, so issued credentials get
        // a managed status list reference
        Credential issued = client.issueCredential(IssueRequest.sdJwt("urn:test:status:1")
                .claim("name", "Status Test"));

        try {
            assertThat(issued.status()).isNotNull();
            assertThat(issued.status().managed()).isTrue();
            assertThat(issued.status().uri()).isNotBlank();

            CredentialStatusInfo active = client.getCredentialStatus(issued.id());
            assertThat(active.managed()).isTrue();
            assertThat(active.isRevoked()).isFalse();

            client.revokeCredential(issued.id());
            CredentialStatusInfo revoked = client.getCredentialStatus(issued.id());
            assertThat(revoked.isRevoked()).isTrue();

            client.unrevokeCredential(issued.id());
            assertThat(client.getCredentialStatus(issued.id()).isRevoked()).isFalse();
        } finally {
            client.deleteCredential(issued.id());
        }
    }

    @Test
    void templatesCanBeListedSavedAndDeleted() {
        WalletClient client = wallet.client();

        assertThat(client.getTemplates())
                .filteredOn(CredentialTemplate::predefined)
                .extracting(CredentialTemplate::name)
                .contains("german-pid-sdjwt", "german-pid-mdoc");

        CredentialTemplate saved = client.saveTemplate(CredentialTemplate.named("test-template")
                .withFormat("sdjwt")
                .withVct("urn:test:template:1")
                .withClaims(Map.of("given_name", "Templated")));
        try {
            assertThat(saved.name()).isEqualTo("test-template");
            assertThat(client.getTemplate("test-template").vct()).isEqualTo("urn:test:template:1");

            Credential issued = client.issueCredential(IssueRequest.fromTemplate("test-template"));
            assertThat(issued.type()).isEqualTo("urn:test:template:1");
            assertThat(issued.claims()).containsEntry("given_name", "Templated");
            client.deleteCredential(issued.id());
        } finally {
            client.deleteTemplate("test-template");
        }

        assertThatThrownBy(() -> client.getTemplate("test-template"))
                .isInstanceOf(WalletClientException.class);
    }

    @Test
    void configExposesInstanceIntrospection() {
        WalletConfig config = wallet.client().getConfig();

        assertThat(config.port()).isEqualTo(8085);
        assertThat(config.autoAccept()).isTrue();
        assertThat(config.validationMode()).isEqualTo("debug");
        assertThat(config.vciVersion()).isEqualTo("1.0");
        assertThat(config.requireHaip()).isFalse();
        assertThat(config.requireHaipIssuance()).isFalse();
        assertThat(config.requireEncryptedRequest()).isFalse();
        assertThat(config.forceClientAttestation()).isFalse();
        assertThat(config.credentialCount()).isGreaterThan(0);
        assertThat(config.buildId()).isNotBlank();
        assertThat(config.version()).isNotBlank();
        // no external TLS terminator, so the built-in HTTPS listener serves
        // getHttpsBaseUrl()
        assertThat(config.tlsListener()).isTrue();
    }

    @Test
    void conformanceSettingsCanBeChangedAndReset() {
        WalletClient client = wallet.client();

        try {
            client.setVciVersion(VciVersion.V1_1);
            client.setValidationMode(ValidationMode.STRICT);
            client.setRequireHaip(true);
            client.setRequireEncryptedRequest(true);

            WalletConfig changed = client.getConfig();
            assertThat(changed.vciVersion()).isEqualTo("1.1");
            assertThat(changed.validationMode()).isEqualTo("strict");
            assertThat(changed.requireHaip()).isTrue();
            assertThat(changed.requireEncryptedRequest()).isTrue();
        } finally {
            client.resetConformance();
        }

        WalletConfig restored = wallet.client().getConfig();
        assertThat(restored.vciVersion()).isEqualTo("1.0");
        assertThat(restored.validationMode()).isEqualTo("debug");
        assertThat(restored.requireHaip()).isFalse();
        assertThat(restored.requireEncryptedRequest()).isFalse();
    }

    @Test
    void activityLogCanBeReadAndCleared() {
        WalletClient client = wallet.client();

        // trigger at least one loggable action
        client.getCredentials();
        assertThat(client.getActivityLog()).isNotNull();

        client.clearActivityLog();
        assertThat(client.getActivityLog()).isNotNull();
    }

    @Test
    void pendingRequestsAreEmptyOnAutoAcceptWallet() {
        assertThat(wallet.client().getPendingRequests()).isEmpty();
    }

    @Test
    void apiSubmissionsAutoAcceptEvenWithoutAutoAccept() throws Exception {
        try (EudiWalletContainer consentWallet = new EudiWalletContainer().withoutAutoAccept()) {
            consentWallet.start();
            WalletClient client = consentWallet.client();
            assertThat(client.getConfig().autoAccept()).isFalse();

            // since eudi-dev v1.18.0 a plain API submission is the caller's own
            // consent, so it imports without raising a consent request
            int countBefore = client.getCredentials().size();
            client.acceptCredentialOffer(demoCredentialOffer(consentWallet));

            assertThat(client.getPendingRequests()).isEmpty();
            assertThat(client.getCredentials()).hasSize(countBefore + 1);
        }
    }

    @Test
    void interactiveOfferSubmissionCanBeApprovedAndDenied() throws Exception {
        try (EudiWalletContainer consentWallet = new EudiWalletContainer().withoutAutoAccept()) {
            consentWallet.start();
            WalletClient client = consentWallet.client();
            int countBefore = client.getCredentials().size();

            CompletableFuture<OfferResponse> approved =
                    client.submitCredentialOfferForConsent(demoCredentialOffer(consentWallet));
            ConsentRequest request = client.awaitPendingRequest(Duration.ofSeconds(10));
            assertThat(request.type()).isEqualTo("issuance");
            assertThat(request.status()).isEqualTo("pending");

            ApprovalResult result = client.approveRequest(request.id());
            assertThat(result.status()).isEqualTo("approved");
            assertThat(approved.get(10, TimeUnit.SECONDS).rawBody()).contains("credential_id");
            assertThat(client.getPendingRequests()).isEmpty();
            assertThat(client.getCredentials()).hasSize(countBefore + 1);

            CompletableFuture<OfferResponse> denied =
                    client.submitCredentialOfferForConsent(demoCredentialOffer(consentWallet));
            client.denyRequest(client.awaitPendingRequest(Duration.ofSeconds(10)).id());

            assertThat(denied.get(10, TimeUnit.SECONDS).rawBody()).contains("\"status\":\"denied\"");
            assertThat(client.getCredentials()).hasSize(countBefore + 1);
        }
    }

    @Test
    void awaitPendingRequestTimesOutWhenNothingIsPending() {
        assertThatThrownBy(() -> wallet.client().awaitPendingRequest(Duration.ofMillis(200)))
                .isInstanceOf(WalletClientException.class)
                .hasMessageContaining("No consent request appeared");
    }

    @Test
    void interactiveAuthorizationRedeemsOfferByPresentingPid() throws Exception {
        try (EudiWalletContainer vciWallet = new EudiWalletContainer()
                .withVciVersion(VciVersion.V1_1)) {
            vciWallet.start();
            WalletClient client = vciWallet.client();
            assertThat(client.getConfig().vciVersion()).isEqualTo("1.1");

            // an authorization-code offer whose issuer requires a PID
            // presentation at its authorization challenge endpoint
            // (OpenID4VCI 1.1 §6) instead of a browser sign-in
            String offerUri = demoCredentialOffer(vciWallet,
                    "?grant=authorization_code&authorization=presentation");

            OfferResponse response = client.acceptCredentialOffer(offerUri);

            assertThat(response.rawBody()).contains("credential_id");
            // the demo issuer only issues its ticket once the wallet's PID
            // presentation was verified, so holding it proves the interactive
            // authorization ran end to end
            assertThat(client.hasCredentialWithType("urn:eudi-test:demo-ticket:1")).isTrue();
        }
    }

    @Test
    void interactiveAuthorizationRaisesSeparateConsentForMidIssuancePresentation() throws Exception {
        try (EudiWalletContainer vciWallet = new EudiWalletContainer()
                .withVciVersion(VciVersion.V1_1)
                .withoutAutoAccept()) {
            vciWallet.start();
            WalletClient client = vciWallet.client();

            String offerUri = demoCredentialOffer(vciWallet,
                    "?grant=authorization_code&authorization=presentation");
            CompletableFuture<OfferResponse> submitted = client.submitCredentialOfferForConsent(offerUri);

            // approving the offer is not consent to disclose: the PID
            // presentation the issuer demands raises its own request, and the
            // offer approval only returns once that request is answered too
            ConsentRequest offer = client.awaitPendingRequest(Duration.ofSeconds(10));
            assertThat(offer.type()).isEqualTo("issuance");
            CompletableFuture<ApprovalResult> offerApproved = client.approveRequestAsync(offer.id());

            ConsentRequest presentation = client.awaitPendingRequest(Duration.ofSeconds(10));
            assertThat(presentation.type()).isEqualTo("issuance_presentation");
            assertThat(presentation.matchedCredentials()).isNotEmpty();
            client.approveRequest(presentation.id());

            assertThat(offerApproved.get(10, TimeUnit.SECONDS).status()).isEqualTo("approved");
            submitted.get(10, TimeUnit.SECONDS);
            assertThat(client.hasCredentialWithType("urn:eudi-test:demo-ticket:1")).isTrue();
        }
    }

    @Test
    void interactiveConsentPicksCredentialAndSelectsClaims() throws Exception {
        try (EudiWalletContainer consentWallet = new EudiWalletContainer().withoutAutoAccept()) {
            consentWallet.start();
            WalletClient client = consentWallet.client();

            // a second SD-JWT PID, so the verifier's query has two candidates
            Credential second = client.issueCredential(IssueRequest.fromTemplate("pid-sdjwt")
                    .claim("given_name", "Second"));
            String sdJwtPidRequest = "{\"type\": \"pid\", \"format\": \"sd-jwt\"}";

            // pick the second credential instead of the wallet's first choice
            Map<String, Object> request = createVerifierRequest(consentWallet, sdJwtPidRequest);
            CompletableFuture<PresentationResponse> submitted =
                    client.submitPresentationRequestForConsent((String) request.get("scheme_uri"));

            ConsentRequest pending = client.awaitPendingRequest(Duration.ofSeconds(10));
            assertThat(pending.type()).isEqualTo("presentation");
            assertThat(pending.matchedCredentials()).isNotEmpty();
            ConsentQueryOptions query = pending.credentialOptions().query("pid");
            assertThat(query.candidates())
                    .extracting(CredentialMatch::credentialId)
                    .contains(second.id());

            ApprovalResult result = client.approveRequest(pending.id(), ConsentApproval.approval()
                    .pickCredential(query.id(), second.id()));
            assertThat(result.status()).isEqualTo("approved");
            submitted.get(10, TimeUnit.SECONDS);

            Map<String, Object> status = verifierRequestStatus(consentWallet, (String) request.get("id"));
            assertThat(status.get("status")).isEqualTo("verified");
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = (Map<String, Object>) status.get("claims");
            assertThat(claims.get("given_name")).isEqualTo("Second");

            // disclose only given_name: the verifier notices that the
            // requested family_name was withheld
            Map<String, Object> partialRequest = createVerifierRequest(consentWallet, sdJwtPidRequest);
            CompletableFuture<PresentationResponse> partialSubmitted =
                    client.submitPresentationRequestForConsent((String) partialRequest.get("scheme_uri"));

            ConsentRequest partialPending = client.awaitPendingRequest(Duration.ofSeconds(10));
            ApprovalResult partialResult = client.approveRequest(partialPending.id(),
                    ConsentApproval.approval()
                            .pickCredential("pid", second.id())
                            .selectClaims(second.id(), "given_name"));
            assertThat(partialResult.status()).isEqualTo("approved");
            partialSubmitted.get(10, TimeUnit.SECONDS);

            Map<String, Object> partialStatus =
                    verifierRequestStatus(consentWallet, (String) partialRequest.get("id"));
            assertThat(partialStatus.get("status")).isEqualTo("failed");
            assertThat((String) partialStatus.get("error")).contains("family_name");
        }
    }

    @Test
    void interactiveConsentChoosesCredentialSetOption() throws Exception {
        try (EudiWalletContainer consentWallet = new EudiWalletContainer().withoutAutoAccept()) {
            consentWallet.start();
            WalletClient client = consentWallet.client();

            // both formats requested: one credential set whose options are
            // the SD-JWT PID and the mdoc PID
            Map<String, Object> request = createVerifierRequest(consentWallet, "{\"type\": \"pid\"}");
            CompletableFuture<PresentationResponse> submitted =
                    client.submitPresentationRequestForConsent((String) request.get("scheme_uri"));

            ConsentRequest pending = client.awaitPendingRequest(Duration.ofSeconds(10));
            // the wallet's own choice is the first option, the SD-JWT PID
            assertThat(pending.matchedCredentials().getFirst().format()).isEqualTo("dc+sd-jwt");
            List<ConsentSetOptions> sets = pending.credentialOptions().sets();
            assertThat(sets).hasSize(1);
            List<List<String>> options = sets.getFirst().options();
            int mdocOption = options.indexOf(List.of("pid_mdoc"));
            assertThat(mdocOption).isNotNegative();

            ApprovalResult result = client.approveRequest(pending.id(), ConsentApproval.approval()
                    .chooseSetOption(0, mdocOption));
            assertThat(result.status()).isEqualTo("approved");
            submitted.get(10, TimeUnit.SECONDS);

            Map<String, Object> status = verifierRequestStatus(consentWallet, (String) request.get("id"));
            assertThat(status.get("status")).isEqualTo("verified");
            // the activity log names what was sent: the mdoc PID, not the
            // SD-JWT one the wallet would have chosen on its own
            boolean sentMdoc = client.getActivityLog().stream()
                    .filter(entry -> entry.details().get("sent_credentials") instanceof List)
                    .flatMap(entry -> ((List<?>) entry.details().get("sent_credentials")).stream())
                    .anyMatch(sent -> sent instanceof Map<?, ?> sentMap
                            && "mso_mdoc".equals(sentMap.get("format")));
            assertThat(sentMdoc).isTrue();
        }
    }

    @Test
    void offerRequiringTransactionCodeNeedsTheCode() throws Exception {
        WalletClient client = wallet.client();

        // a real pre-authorized offer from the demo issuer, rewritten to
        // demand a transaction code (the demo issuer itself never asks one)
        HttpResponse<String> created = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(wallet.getBaseUrl() + "/issuer/api/offers"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        Map<String, Object> offerRef = MAPPER.readValue(created.body(), new TypeReference<>() {});
        HttpResponse<String> offerDoc = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(wallet.getBaseUrl() + "/issuer/offer/" + offerRef.get("id")))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Map<String, Object> offer = MAPPER.readValue(offerDoc.body(), new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> preAuthGrant = (Map<String, Object>)
                ((Map<String, Object>) offer.get("grants"))
                        .get("urn:ietf:params:oauth:grant-type:pre-authorized_code");
        preAuthGrant.put("tx_code", Map.of("length", 4, "input_mode", "numeric"));
        String offerUri = "openid-credential-offer://?credential_offer="
                + java.net.URLEncoder.encode(MAPPER.writeValueAsString(offer), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> client.acceptCredentialOffer(offerUri))
                .isInstanceOf(WalletClientException.class)
                .hasMessageContaining("transaction code");

        OfferResponse accepted = client.acceptCredentialOffer(offerUri, "1234");
        assertThat(accepted.rawBody()).contains("credential_id");
    }

    /**
     * Creates a presentation request with the wallet's own built-in demo
     * verifier (mounted at {@code /verifier}), so the consent tests need no
     * external verifier.
     */
    private static Map<String, Object> createVerifierRequest(EudiWalletContainer container, String body)
            throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(container.getBaseUrl() + "/verifier/api/requests"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isBetween(200, 299);
        return MAPPER.readValue(response.body(), new TypeReference<>() {});
    }

    private static Map<String, Object> verifierRequestStatus(EudiWalletContainer container, String id)
            throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(container.getBaseUrl() + "/verifier/api/requests/" + id))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isBetween(200, 299);
        return MAPPER.readValue(response.body(), new TypeReference<>() {});
    }

    /**
     * Creates a credential offer with the wallet's own built-in demo issuer
     * (mounted at {@code /issuer} since eudi-dev v1.17.0), so the consent tests
     * need no external issuer.
     */
    private static String demoCredentialOffer(EudiWalletContainer container) throws Exception {
        return demoCredentialOffer(container, "");
    }

    private static String demoCredentialOffer(EudiWalletContainer container, String query) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(container.getBaseUrl() + "/issuer/api/offers" + query))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isBetween(200, 299);
        Map<String, Object> offer = MAPPER.readValue(response.body(), new TypeReference<>() {});
        return (String) offer.get("scheme_uri");
    }

    @Test
    void deleteAllCredentialsRemovesEverything() {
        try (EudiWalletContainer scratchWallet = new EudiWalletContainer()) {
            scratchWallet.start();
            WalletClient client = scratchWallet.client();
            assertThat(client.getCredentials()).isNotEmpty();

            int deleted = client.deleteAllCredentials();

            assertThat(deleted).isGreaterThan(0);
            assertThat(client.getCredentials()).isEmpty();
        }
    }

    private static SSLContext sslContextFromPem(String pem) throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(
                new java.io.ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII))
        );

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        int index = 0;
        for (Certificate certificate : certificates) {
            trustStore.setCertificateEntry("eudi-dev-cert-" + index++, certificate);
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }

    @SuppressWarnings("unchecked")
    private static String trustListLoteType(String jwt) throws IOException {
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSizeGreaterThanOrEqualTo(2);
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, Object> decoded = MAPPER.readValue(payload, new TypeReference<>() {});
        Map<String, Object> lote = (Map<String, Object>) decoded.get("LoTE");
        Map<String, Object> schemeInfo = (Map<String, Object>) lote.get("ListAndSchemeInformation");
        return (String) schemeInfo.get("LoTEType");
    }
}
