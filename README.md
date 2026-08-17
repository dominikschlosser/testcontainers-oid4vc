# testcontainers-eudi

[![Build](https://github.com/dominikschlosser/testcontainers-eudi/actions/workflows/build.yml/badge.svg)](https://github.com/dominikschlosser/testcontainers-eudi/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.dominikschlosser/testcontainers-eudi)](https://central.sonatype.com/artifact/io.github.dominikschlosser/testcontainers-eudi)

A [Testcontainers](https://www.testcontainers.org/) module for testing [OpenID for Verifiable Credentials (OID4VC)](https://openid.net/sg/openid4vc/) implementations. It wraps the [`eudi-dev`](https://github.com/dominikschlosser/eudi-dev) Docker image, providing a containerized EUDI test wallet for OID4VCI and OID4VP integration tests.

> **Renamed from testcontainers-oid4vc:** the wrapped tool was renamed from `oid4vc-dev` to `eudi-dev`, and this module followed as `testcontainers-eudi` starting with version 2.0.0. The Java package moved to `io.github.dominikschlosser.eudi` and the container class is now `EudiWalletContainer`. Versions up to 1.6.0 remain available as `testcontainers-oid4vc`.

## Requirements

- Java 21+
- Docker

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.dominikschlosser</groupId>
    <artifactId>testcontainers-eudi</artifactId>
    <version>2.2.0</version>
    <scope>test</scope>
</dependency>
```

The artifact is published to [Maven Central](https://central.sonatype.com/artifact/io.github.dominikschlosser/testcontainers-eudi). No additional repository configuration is needed.

## Usage

### Basic setup

```java
@Testcontainers
class MyEudiTest {

    @Container
    static EudiWalletContainer wallet = new EudiWalletContainer();

    @Test
    void testCredentials() {
        List<Credential> credentials = wallet.listCredentials();
        assertThat(credentials).isNotEmpty();
    }
}
```

By default, the container starts with a pre-configured PID credential and auto-accept enabled.
It uses `ghcr.io/dominikschlosser/eudi-dev:v1.24.2` by default. If you need a different image tag, pass it to the constructor.

### Configuration

The container supports fluent configuration:

```java
EudiWalletContainer wallet = new EudiWalletContainer()
    .withStatusList()                                // enable status list endpoint
    .withBaseUrl("http://wallet.example.test:8085")  // advertised HTTP base URL; HTTPS host follows this too
    .withPreferredFormat(CredentialFormat.SD_JWT)    // set preferred credential format
    .withRequireEncryptedRequest()                   // require encrypted request objects (wallet_metadata)
    .withHaip()                                      // enforce HAIP 1.0 compliance
    .withStrictValidation()                          // fail flows on spec violations instead of tolerating them
    .withVciVersion(VciVersion.V1_1)                 // OpenID4VCI feature level (enables interactive authorization)
    .withVciClientId("my-wallet")                    // client id for OID4VCI authorization-code flows
    .withVciRedirectUri("http://localhost:8085/callback")
    .withPidType("urn:eudi:pid:de:1")                // generate the German PID instead of the country-independent one
    .withoutAutoAccept()                             // disable auto-accept mode
    .withoutDefaultPid();                            // disable default PID credential
```

Validation mode, HAIP, the encrypted-request requirement and the OpenID4VCI
feature level are also changeable at runtime, without restarting the
container:

```java
WalletClient client = wallet.client();
client.setValidationMode(ValidationMode.STRICT);
client.setRequireHaip(true);
client.setRequireEncryptedRequest(true);
client.setVciVersion(VciVersion.V1_1);
client.resetConformance();      // restore the settings the wallet started with
```

#### Custom PID claims

Use a format-specific claims builder:

```java
// SD-JWT format
EudiWalletContainer wallet = new EudiWalletContainer()
    .withPidClaims(new SdJwtPidClaims()
        .givenName("Jane")
        .familyName("Doe")
        .birthdate("1990-01-15")
        .nationalities("DE"));

// mDoc format
EudiWalletContainer wallet = new EudiWalletContainer()
    .withPidClaims(new MdocPidClaims()
        .givenName("Jane")
        .familyName("Doe")
        .birthDate("1990-01-15")
        .nationality("DE"));
```

Or provide raw JSON:

```java
EudiWalletContainer wallet = new EudiWalletContainer()
    .withPidClaims("{\"given_name\": \"Jane\", \"family_name\": \"Doe\"}");
```

The builders mirror the EUDI PID Rulebook attributes the default PID carries.
`withPidType(...)` selects another PID type — `urn:eudi:pid:de:1` generates
the German PID with its national additions, which are set through the generic
`claim(name, value)` escape hatch:

```java
EudiWalletContainer wallet = new EudiWalletContainer()
    .withPidType("urn:eudi:pid:de:1")
    .withPidClaims(new SdJwtPidClaims()
        .givenName("Jane")
        .claim("birth_name", "Doe"));
```

### Wallet client

The container provides a `WalletClient` for interacting with the wallet API:

```java
WalletClient client = wallet.client();

// List credentials
List<Credential> credentials = client.getCredentials();

// Fetch a single credential
Credential credential = client.getCredential(credentials.getFirst().id());

// Get the legacy default trust list / status list
String trustListJwt = client.getTrustList();
String statusListJwt = client.getStatusList();

// Inspect all registered trust-list profiles
List<TrustListIndexEntry> trustLists = client.getTrustLists();

// Resolve the container-friendly discovery path to the mapped host URL
String reachableLocalTrustListUrl = trustLists.stream()
    .filter(entry -> "local".equals(entry.id()))
    .findFirst()
    .map(wallet::resolveTrustListUrl)
    .orElseThrow();

// Select a specific trust list
String localTrustListJwt = client.getTrustListById("local");
String pidTrustListByDocType = client.getTrustListForDocType("eu.europa.ec.eudi.pid.1");
String customTrustListByVct = client.getTrustListForVct("urn:example:my-credential:1");
```

### Issuing credentials

The wallet can issue credentials with its own issuer key and import them
directly — no separate issuer needed. Issue from one of the pre-defined
templates (`pid-sdjwt`, `pid-mdoc`, `german-pid-sdjwt`, `german-pid-mdoc`), a
saved template, or free-form:

```java
WalletClient client = wallet.client();

// From a pre-defined template, overriding individual claims
Credential pid = client.issueCredential(IssueRequest.fromTemplate("german-pid-sdjwt")
    .claim("given_name", "Jane"));

// Free-form SD-JWT credential
Credential cred = client.issueCredential(IssueRequest.sdJwt("urn:example:my-credential:1")
    .claim("given_name", "Jane")
    .claim("family_name", "Doe")
    .alwaysDisclosed("given_name")   // embed plainly, not selectively disclosable
    .ttl(Duration.ofHours(1)));

// mDoc with per-attribute namespaces (namespace:element claim keys)
Credential mdoc = client.issueCredential(IssueRequest.mdoc("org.iso.23220.photoid.1")
    .namespace("org.iso.23220.1")
    .claim("given_name", "Jane")
    .claim("org.iso.23220.photoid.1:person_id", "12345"));
```

### Credential templates

Templates are named, reusable claim sets shared between the CLI, the HTTP API,
and the wallet UI:

```java
WalletClient client = wallet.client();

// List all templates (pre-defined and user)
List<CredentialTemplate> templates = client.getTemplates();

// Save a template and issue from it
client.saveTemplate(CredentialTemplate.named("my-credential")
    .withFormat("sdjwt")
    .withVct("urn:example:my-credential:1")
    .withClaims(Map.of("given_name", "Jane")));

Credential issued = client.issueCredential(IssueRequest.fromTemplate("my-credential"));

client.deleteTemplate("my-credential");
```

To ship a project folder of template JSON files into the container, use
`withTemplatesDirectory(Path)`:

```java
EudiWalletContainer wallet = new EudiWalletContainer()
    .withTemplatesDirectory(Path.of("src/test/resources/templates"));
```

### OID4VCI / OID4VP flows

```java
// Accept a credential offer (returns OfferResponse)
OfferResponse offer = wallet.acceptCredentialOffer(credentialOfferUri);

// Accept a presentation request (returns PresentationResponse with redirect URI)
PresentationResponse presentation = wallet.acceptPresentationRequest(presentationRequestUri);
String redirectUri = presentation.redirectUri();
String idToken = presentation.idToken(); // available when response_type includes id_token
```

Offers can also be delivered to the wallet's own URL instead of the
`openid-credential-offer://` custom scheme — useful for verifiers or issuers
that only speak plain web URLs:

```java
String offerEndpoint = wallet.getCredentialOfferUrl(); // http://host:port/credential-offer
```

### Consent handling without auto-accept

Consent is per channel. `acceptCredentialOffer(...)` and
`acceptPresentationRequest(...)` always accept, even with
`withoutAutoAccept()` — the API call is the caller's own consent. Requests
arriving through interactive channels (the wallet's `/credential-offer` and
`/authorize` URLs, or a custom-scheme dispatch) wait as pending consent
requests on a wallet started with `withoutAutoAccept()`.

To drive that consent dialog from a test, submit the offer or request as an
interactive one. The wallet holds the submission open until it is decided, so
the result arrives as a `CompletableFuture`:

```java
WalletClient client = wallet.client();

CompletableFuture<OfferResponse> submitted = client.submitCredentialOfferForConsent(offerUri);

ConsentRequest request = client.awaitPendingRequest(Duration.ofSeconds(10));
ApprovalResult result = client.approveRequest(request.id());
String redirectUri = result.redirectUri();

OfferResponse response = submitted.get();
```

`submitPresentationRequestForConsent(...)` does the same for OID4VP requests,
and `approveRequest(id, selectedClaims)` discloses only the selected claims per
credential id. Denying resolves the submission with a `denied` status instead:

```java
client.denyRequest(request.id());
```

`getPendingRequests()` lists everything currently awaiting a decision.

### Credential management

```java
WalletClient client = wallet.client();

// Delete a specific credential
client.deleteCredential(credentialId);

// Delete everything
int deleted = client.deleteAllCredentials();

// Check if a credential with a given type exists
boolean hasPid = client.hasCredentialWithType("urn:eu.europa.ec.eudi:pid:1");

// Delete all credentials matching a type
client.deleteCredentialsByType("urn:eu.europa.ec.eudi:pid:1");
```

### Revocation and status lists

Credentials issued by a wallet running `withStatusList()` carry a managed
status list reference, visible on `Credential.status()`:

```java
WalletClient client = wallet.client();
Credential issued = client.issueCredential(IssueRequest.sdJwt("urn:example:my-credential:1")
    .claim("name", "Revocable"));

client.revokeCredential(issued.id());
CredentialStatusInfo status = client.getCredentialStatus(issued.id());
assertThat(status.isRevoked()).isTrue();

client.unrevokeCredential(issued.id());
```

`getCredentialStatus` also resolves credentials referencing external status
lists by fetching them live.

### Wallet introspection

```java
WalletClient client = wallet.client();

// Full instance configuration: version, ports, URLs, validation mode, credential count, ...
WalletConfig config = client.getConfig();

// Activity log: assert what the wallet actually did during a flow
List<ActivityLogEntry> log = client.getActivityLog();
client.clearActivityLog();
```

### SD-JWT credential builder

Create signed SD-JWT credentials for test scenarios without running an issuer:

```java
SdJwtCredentialBuilder builder = new SdJwtCredentialBuilder();

String sdJwt = builder
    .vct("urn:example:my-credential:1")
    .issuer("https://issuer.example.com")
    .claim("given_name", "Jane")
    .claim("family_name", "Doe")
    .objectClaim("address", Map.of("street", "123 Main St", "city", "Berlin"))
    .arrayClaim("nationalities", List.of("DE", "US"))
    .ttl(Duration.ofHours(1))
    .build();

// Import into wallet
wallet.client().importCredential(sdJwt);

// Access the signing key (e.g. for verifier trust configuration)
ECKey issuerKey = builder.getSigningKey();
```

Unlike `issueCredential`, the builder signs with its own generated key on the
host, so the wallet does not know the issuer — useful for negative tests and
custom trust setups.

### Host access

When your issuer or verifier runs on the host machine, use `withHostAccess()` so the wallet container can reach `localhost` on the host:

```java
@Container
static EudiWalletContainer wallet = new EudiWalletContainer()
    .withHostAccess();

@BeforeAll
static void setup() {
    Testcontainers.exposeHostPorts(8080);
}
```

Under the hood this uses Docker's `host-gateway` special address (`--add-host=localhost:host-gateway`), which the Docker daemon resolves to the host's internal IP. The service is then reachable from within the container at `localhost:8080`.

### Convenience URLs

```java
wallet.getBaseUrl();         // http://host:port
wallet.getHttpsBaseUrl();    // https://host:tls-port
wallet.getAuthorizeUrl();    // http://host:port/authorize
wallet.getHttpsAuthorizeUrl(); // https://host:tls-port/authorize
wallet.getCredentialOfferUrl(); // http://host:port/credential-offer
wallet.getHttpsCredentialOfferUrl(); // https://host:tls-port/credential-offer
wallet.getTrustListUrl();    // http://host:port/api/trustlist
wallet.getTrustListsUrl();   // http://host:port/api/trustlists
wallet.getTrustListUrl("local"); // http://host:port/api/trustlists/local
wallet.getTrustListUrlForVct("urn:example:my-credential:1"); // http://host:port/api/trustlist?vct=...
wallet.getTrustListUrlForDocType("org.iso.23220.photoid.1"); // http://host:port/api/trustlist?doctype=...
wallet.getHttpsTrustListUrl(); // https://host:tls-port/api/trustlist
wallet.getHttpsTrustListsUrl(); // https://host:tls-port/api/trustlists
wallet.getHttpsTrustListUrl("local"); // https://host:tls-port/api/trustlists/local
wallet.getIssuerUrl();       // alias of getHttpsBaseUrl()
wallet.getIssuerMetadataUrl(); // https://host:tls-port/.well-known/jwt-vc-issuer
wallet.getCredentialsUrl();  // http://host:port/api/credentials
wallet.getTemplatesUrl();    // http://host:port/api/templates
wallet.getStatusListUrl();   // http://host:port/api/statuslist
wallet.getHttpsStatusListUrl(); // https://host:tls-port/api/statuslist
```

`/api/trustlists` is a discovery endpoint. Each entry exposes a relative `path` plus an `advertisedUrl` / `url` alias. For Docker and Testcontainers callers, prefer resolving `path` against the mapped wallet URL via `TrustListIndexEntry.resolveUrl(...)` or `EudiWalletContainer.resolveTrustListUrl(...)`. `/api/trustlist` remains the backward-compatible PID-first endpoint, and `/api/trustlists/{id}` plus the legacy `vct` / `doctype` selectors still fetch the actual ETSI trust-list JWTs.

### Trust the wallet HTTPS certificate

`eudi-dev` exposes both the wallet HTTPS leaf certificate and the shared wallet CA certificate, each as PEM or JWKS. Use the leaf certificate if you only need to trust one wallet instance. Use the CA certificate if your test harness must trust auxiliary wallets signed by the same CA:

```java
EudiWalletContainer wallet = new EudiWalletContainer();

String leafPem = wallet.getWalletTlsCertificatePem();
Path leafCertFile = wallet.exportWalletTlsCertificate(Path.of("target/eudi-wallet.pem"));

String caPem = wallet.getWalletTlsCaCertificatePem();
Path caCertFile = wallet.exportWalletTlsCaCertificate(Path.of("target/eudi-wallet-ca.pem"));

// JWKS form (public key with x5c chain), e.g. for Keycloak trust configuration
String leafJwks = wallet.getWalletTlsCertificateJwks();
String caJwks = wallet.getWalletTlsCaCertificateJwks();

String issuerMetadataUrl = wallet.getIssuerMetadataUrl();
String trustListUrl = wallet.getHttpsTrustListUrl();
String statusListUrl = wallet.getHttpsStatusListUrl();
```

Add the appropriate exported PEM to your verifier's trust store instead of disabling TLS verification.

## License

[Apache 2.0](LICENSE)
