# testcontainers-eudi

[![Build](https://github.com/dominikschlosser/testcontainers-eudi/actions/workflows/build.yml/badge.svg)](https://github.com/dominikschlosser/testcontainers-eudi/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.dominikschlosser/testcontainers-eudi)](https://central.sonatype.com/artifact/io.github.dominikschlosser/testcontainers-eudi)

A [Testcontainers](https://www.testcontainers.org/) module for testing [OpenID for Verifiable Credentials (OID4VC)](https://openid.net/sg/openid4vc/) implementations. It wraps the [`eudi-dev`](https://github.com/dominikschlosser/eudi-dev) Docker image and gives your integration tests a complete EUDI test wallet for OID4VCI and OID4VP flows.

> **Renamed from testcontainers-oid4vc:** the wrapped tool was renamed from `oid4vc-dev` to `eudi-dev`, and this module followed as `testcontainers-eudi` starting with version 2.0.0. The Java package moved to `io.github.dominikschlosser.eudi` and the container class is now `EudiWalletContainer`. Versions up to 1.6.0 remain available as `testcontainers-oid4vc`.

## Requirements

- Java 21+
- Docker

## Installation

The artifact is published to [Maven Central](https://central.sonatype.com/artifact/io.github.dominikschlosser/testcontainers-eudi):

```xml
<dependency>
    <groupId>io.github.dominikschlosser</groupId>
    <artifactId>testcontainers-eudi</artifactId>
    <version>2.4.0</version>
    <scope>test</scope>
</dependency>
```

## Getting started

```java
@Testcontainers
class MyEudiTest {

    @Container
    static EudiWalletContainer wallet = new EudiWalletContainer();

    @Test
    void walletHoldsPid() {
        List<Credential> credentials = wallet.listCredentials();
        assertThat(credentials).isNotEmpty();
    }
}
```

The container starts `ghcr.io/dominikschlosser/eudi-dev:v2.2.0` with a pre-configured PID credential (SD-JWT and mdoc) and auto-accept enabled. Pass a different image tag to the constructor if you need one. `wallet.acceptCredentialOffer(uri)` and `wallet.acceptPresentationRequest(uri)` run complete OID4VCI and OID4VP flows against your issuer or verifier, see [OID4VCI and OID4VP flows](docs/flows.md).

## Documentation

- [Configuration](docs/configuration.md): startup options, runtime conformance switches, custom PID claims
- [Credentials](docs/credentials.md): issuing, templates, importing, revocation, the SD-JWT builder
- [OID4VCI and OID4VP flows](docs/flows.md): offers, presentations, consent handling
- [Trust](docs/trust.md): trust lists, TLS certificates, issuer metadata
- [Networking and URLs](docs/networking.md): endpoint URLs, host access, base URL

## License

[Apache 2.0](LICENSE)
