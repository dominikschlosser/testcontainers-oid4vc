<p align="center">
  <img src="docs/assets/logo-mark.svg" alt="eudi-dev logo" width="110">
</p>

# testcontainers-eudi

[![Build](https://github.com/dominikschlosser/testcontainers-eudi/actions/workflows/build.yml/badge.svg)](https://github.com/dominikschlosser/testcontainers-eudi/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.dominikschlosser/testcontainers-eudi)](https://central.sonatype.com/artifact/io.github.dominikschlosser/testcontainers-eudi)

A [Testcontainers](https://www.testcontainers.org/) module that puts a complete EUDI test wallet into your integration tests, wrapping the [`eudi-dev`](https://github.com/dominikschlosser/eudi-dev) Docker image.

If you build an issuer, a verifier, or an identity provider that speaks [OpenID for Verifiable Credentials](https://openid.net/sg/openid4vc/), your tests need a wallet on the other side of the protocol. This one starts in about a second, already holds a PID credential in SD-JWT and mdoc form, redeems credential offers, answers presentation requests, and exposes an HTTP API your test drives through a typed Java client: seed exactly the credentials a scenario needs, walk through consent, and assert what the wallet actually sent and received.

## Installation

Requires Java 21 and Docker. The artifact is on [Maven Central](https://central.sonatype.com/artifact/io.github.dominikschlosser/testcontainers-eudi):

```xml
<dependency>
    <groupId>io.github.dominikschlosser</groupId>
    <artifactId>testcontainers-eudi</artifactId>
    <version>2.4.0</version>
    <scope>test</scope>
</dependency>
```

The container runs `ghcr.io/dominikschlosser/eudi-dev:v2.2.0` by default. Pass a different image tag to the constructor to pin another wallet version.

## Test your verifier

Your application asks the user for a credential over OID4VP and renders the request as a QR code or deep link. In the test, the wallet takes the user's place: hand it the request URI and it presents its PID, then you assert what your application made of it.

```java
@Testcontainers
class VerifierLoginTest {

    @Container
    static EudiWalletContainer wallet = new EudiWalletContainer();

    @Test
    void userLogsInByPresentingPid() {
        String requestUri = myVerifier.createPresentationRequest(); // openid4vp://...

        PresentationResponse response = wallet.acceptPresentationRequest(requestUri);

        // response.redirectUri() is where the user's browser would go next,
        // follow it when the test needs the resulting session
        assertThat(myVerifier.verifiedClaims())
                .containsEntry("given_name", "Jan Wijnand")
                .containsEntry("family_name", "'t Hart");
    }
}
```

When your application runs on the host, add `withHostAccess()` so the wallet container can reach it on `localhost`, see [Networking](docs/networking.md).

## Test your issuer

The wallet redeems the credential offer your issuer created, like a user scanning the offer QR code, and you assert what landed in the wallet:

```java
@Test
void issuerDeliversEmployeeBadge() {
    String offerUri = myIssuer.createCredentialOffer(); // openid-credential-offer://...

    wallet.acceptCredentialOffer(offerUri);

    Credential badge = wallet.client()
            .getCredentialsByType("urn:example:employee-badge:1").getFirst();
    assertThat(badge.claims()).containsEntry("department", "Engineering");
}
```

Authorization-code flows, wallet attestation, batch and deferred issuance, and interactive authorization (OpenID4VCI 1.1) are handled by the wallet, see [Flows](docs/flows.md).

## Seed the exact wallet state a test needs

The wallet issues credentials to itself, so every scenario starts from a defined state instead of depending on a separate issuer:

```java
WalletClient client = wallet.client(); // wallet started with .withStatusList()

// a credential your verifier should accept
client.issueCredential(IssueRequest.sdJwt("urn:example:diploma:1")
        .claim("degree", "MSc"));

// a revoked one it should reject
Credential revoked = client.issueCredential(IssueRequest.sdJwt("urn:example:diploma:1")
        .claim("degree", "BSc"));
client.revokeCredential(revoked.id());

// one signed by an issuer your verifier does not trust
client.issueCredential(IssueRequest.sdJwt("urn:example:diploma:1")
        .claim("degree", "PhD")
        .signedBy(foreignKeyPem, foreignCertPem));
```

Custom PID claims, mdoc namespaces, credential templates and a host-side SD-JWT builder are covered in [Credentials](docs/credentials.md) and [Configuration](docs/configuration.md).

## Catch spec violations

By default the wallet tolerates spec violations and logs them, so flows keep working while you build. Flip it to strict mode and your test fails on the exact violation, with the specification cited:

```java
@Container
static EudiWalletContainer wallet = new EudiWalletContainer()
        .withStrictValidation()   // fail flows on OID4VP and OID4VCI violations
        .withHaip();              // enforce HAIP 1.0 on top

// and assert what the wallet actually did during a flow
List<ActivityLogEntry> log = wallet.client().getActivityLog();
```

The consent dialog is testable too: start the wallet with `withoutAutoAccept()` and your test makes the decision, choosing the credential and the claims to disclose or denying outright, see [Flows](docs/flows.md).

## Documentation

- [Configuration](docs/configuration.md): startup options, conformance settings, custom PID claims
- [Credentials](docs/credentials.md): issuing, templates, importing, revocation, the SD-JWT builder
- [OID4VCI and OID4VP flows](docs/flows.md): offers, presentations, consent handling
- [Trust](docs/trust.md): trust lists, TLS certificates, issuer metadata
- [Networking and URLs](docs/networking.md): endpoint URLs, host access, base URL

## Notices

**No EU affiliation:** This is an independent open source project. It is not an official repository of the European Commission or the European Union, and it is not affiliated with or endorsed by them. "EUDI" is used descriptively, for a testing tool aimed at the European Digital Identity ecosystem. For official EUDI Wallet resources see the [eu-digital-identity-wallet](https://github.com/eu-digital-identity-wallet) organization.

**Renamed from testcontainers-oid4vc:** The wrapped tool was renamed from `oid4vc-dev` to `eudi-dev`, and this module followed as `testcontainers-eudi` starting with version 2.0.0. The Java package moved to `io.github.dominikschlosser.eudi` and the container class is now `EudiWalletContainer`. Versions up to 1.6.0 remain available as `testcontainers-oid4vc`.

## License

[Apache 2.0](LICENSE)
