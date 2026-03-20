# testcontainers-oid4vc

[![Build](https://github.com/dominikschlosser/testcontainers-oid4vc/actions/workflows/build.yml/badge.svg)](https://github.com/dominikschlosser/testcontainers-oid4vc/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.dominikschlosser/testcontainers-oid4vc)](https://central.sonatype.com/artifact/io.github.dominikschlosser/testcontainers-oid4vc)

A [Testcontainers](https://www.testcontainers.org/) module for testing [OpenID for Verifiable Credentials (OID4VC)](https://openid.net/sg/openid4vc/) implementations. It wraps the [`oid4vc-dev`](https://github.com/dominikschlosser/oid4vc-dev) Docker image, providing a containerized wallet for OID4VCI and OID4VP integration tests.

## Requirements

- Java 21+
- Docker

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.dominikschlosser</groupId>
    <artifactId>testcontainers-oid4vc</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

The artifact is published to [Maven Central](https://central.sonatype.com/artifact/io.github.dominikschlosser/testcontainers-oid4vc). No additional repository configuration is needed.

## Usage

### Basic setup

```java
@Testcontainers
class MyOid4vcTest {

    @Container
    static Oid4vcContainer wallet = new Oid4vcContainer();

    @Test
    void testCredentials() {
        List<Credential> credentials = wallet.listCredentials();
        assertThat(credentials).isNotEmpty();
    }
}
```

By default, the container starts with a pre-configured PID credential and auto-accept enabled.
It uses the latest `ghcr.io/dominikschlosser/oid4vc-dev` image by default. If you need to pin a specific image tag, pass it to the constructor.

### Configuration

The container supports fluent configuration:

```java
Oid4vcContainer wallet = new Oid4vcContainer()
    .withStatusList()                                // enable status list endpoint
    .withBaseUrl("http://wallet.example.test:8085")  // advertised HTTP base URL; HTTPS host follows this too
    .withPreferredFormat(CredentialFormat.SD_JWT)     // set preferred credential format
    .withRequireEncryptedRequest()                   // require encrypted request objects (wallet_metadata)
    .withoutAutoAccept()                             // disable auto-accept mode
    .withoutDefaultPid();                            // disable default PID credential
```

#### Custom PID claims

Use a format-specific claims builder:

```java
// SD-JWT format
Oid4vcContainer wallet = new Oid4vcContainer()
    .withPidClaims(new SdJwtPidClaims()
        .givenName("Jane")
        .familyName("Doe")
        .birthdate("1990-01-15")
        .nationalities("DE"));

// mDoc format
Oid4vcContainer wallet = new Oid4vcContainer()
    .withPidClaims(new MdocPidClaims()
        .givenName("Jane")
        .familyName("Doe")
        .birthDate("1990-01-15")
        .nationality("DE"));
```

Or provide raw JSON:

```java
Oid4vcContainer wallet = new Oid4vcContainer()
    .withPidClaims("{\"given_name\": \"Jane\", \"family_name\": \"Doe\"}");
```

### Wallet client

The container provides a `WalletClient` for interacting with the wallet API:

```java
WalletClient client = wallet.client();

// List credentials
List<Credential> credentials = client.getCredentials();

// Get trust list / status list
String trustListJwt = client.getTrustList();
String statusListJwt = client.getStatusList();
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

### Credential management

```java
WalletClient client = wallet.client();

// Delete a specific credential
client.deleteCredential(credentialId);

// Check if a credential with a given type exists
boolean hasPid = client.hasCredentialWithType("urn:eu.europa.ec.eudi:pid:1");

// Delete all credentials matching a type
client.deleteCredentialsByType("urn:eu.europa.ec.eudi:pid:1");
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

### Host access

When your issuer or verifier runs on the host machine, use `withHostAccess()` so the wallet container can reach `localhost` on the host:

```java
@Container
static Oid4vcContainer wallet = new Oid4vcContainer()
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
wallet.getTrustListUrl();    // http://host:port/api/trustlist
wallet.getHttpsTrustListUrl(); // https://host:tls-port/api/trustlist
wallet.getIssuerUrl();       // alias of getHttpsBaseUrl()
wallet.getIssuerMetadataUrl(); // https://host:tls-port/.well-known/jwt-vc-issuer
wallet.getCredentialsUrl();  // http://host:port/api/credentials
wallet.getStatusListUrl();   // http://host:port/api/statuslist
wallet.getHttpsStatusListUrl(); // https://host:tls-port/api/statuslist
```

### Trust the wallet HTTPS certificate

`oid4vc-dev` v1.5.1 serves wallet metadata, authorize, trust list, and status list endpoints on a self-signed HTTPS listener. The container exposes that certificate:

```java
Oid4vcContainer wallet = new Oid4vcContainer();

String pem = wallet.getWalletTlsCertificatePem();
Path certFile = wallet.exportWalletTlsCertificate(Path.of("target/oid4vc-wallet.pem"));

String issuerMetadataUrl = wallet.getIssuerMetadataUrl();
String trustListUrl = wallet.getHttpsTrustListUrl();
String statusListUrl = wallet.getHttpsStatusListUrl();
```

Add the exported PEM to your verifier's trust store instead of disabling TLS verification.

## License

[Apache 2.0](LICENSE)
