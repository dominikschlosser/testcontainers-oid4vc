# testcontainers-oid4vc

[![Build](https://github.com/dominikschlosser/testcontainers-oid4vc/actions/workflows/build.yml/badge.svg)](https://github.com/dominikschlosser/testcontainers-oid4vc/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/dominikschlosser/testcontainers-oid4vc)](https://github.com/dominikschlosser/testcontainers-oid4vc/releases/latest)

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
    <version>0.13.3</version>
    <scope>test</scope>
</dependency>
```

The artifact is published to [GitHub Packages](https://github.com/dominikschlosser/testcontainers-oid4vc/packages). You need to add the GitHub Packages repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/dominikschlosser/testcontainers-oid4vc</url>
    </repository>
</repositories>
```

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

### Configuration

The container supports fluent configuration:

```java
Oid4vcContainer wallet = new Oid4vcContainer()
    .withStatusList()                                // enable status list endpoint
    .withPreferredFormat(CredentialFormat.SD_JWT)     // set preferred credential format
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
wallet.getAuthorizeUrl();    // http://host:port/authorize
wallet.getCredentialsUrl();  // http://host:port/api/credentials
wallet.getTrustListUrl();    // http://host:port/api/trustlist
wallet.getStatusListUrl();   // http://host:port/api/statuslist
```

## License

[Apache 2.0](LICENSE)
