# testcontainers-oid4vc

[![Build](https://github.com/dominikschlosser/testcontainers-oid4vc/actions/workflows/build.yml/badge.svg)](https://github.com/dominikschlosser/testcontainers-oid4vc/actions/workflows/build.yml)
[![Release](https://github.com/dominikschlosser/testcontainers-oid4vc/actions/workflows/release.yml/badge.svg)](https://github.com/dominikschlosser/testcontainers-oid4vc/actions/workflows/release.yml)

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
    <version>0.1.0</version>
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
// Accept a credential offer
wallet.acceptCredentialOffer(credentialOfferUri);

// Accept a presentation request
wallet.acceptPresentationRequest(presentationRequestUri);
```

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
