# Trust

## Trust lists

The wallet serves ETSI trust lists for the credentials it issues:

```java
WalletClient client = wallet.client();

// The default trust list
String trustListJwt = client.getTrustList();

// All registered trust-list profiles
List<TrustListIndexEntry> trustLists = client.getTrustLists();

// Select a specific trust list
String localTrustListJwt = client.getTrustListById("local");
String pidTrustListByDocType = client.getTrustListForDocType("eu.europa.ec.eudi.pid.1");
String customTrustListByVct = client.getTrustListForVct("urn:example:my-credential:1");
```

`/api/trustlists` is a discovery endpoint. Each entry exposes a relative `path` plus an `advertisedUrl` / `url` alias. From Docker and Testcontainers callers, resolve `path` against the mapped wallet URL:

```java
String reachableLocalTrustListUrl = client.getTrustLists().stream()
    .filter(entry -> "local".equals(entry.id()))
    .findFirst()
    .map(wallet::resolveTrustListUrl)
    .orElseThrow();
```

`/api/trustlist` remains the backward-compatible PID-first endpoint, and `/api/trustlists/{id}` plus the `vct` / `doctype` selectors fetch the actual ETSI trust-list JWTs.

## TLS certificates

The wallet exposes both its HTTPS leaf certificate and the shared wallet CA certificate, each as PEM or JWKS. Use the leaf certificate to trust one wallet instance. Use the CA certificate if your test harness must trust auxiliary wallets signed by the same CA:

```java
EudiWalletContainer wallet = new EudiWalletContainer();

String leafPem = wallet.getWalletTlsCertificatePem();
Path leafCertFile = wallet.exportWalletTlsCertificate(Path.of("target/eudi-wallet.pem"));

String caPem = wallet.getWalletTlsCaCertificatePem();
Path caCertFile = wallet.exportWalletTlsCaCertificate(Path.of("target/eudi-wallet-ca.pem"));

// JWKS form (public key with x5c chain), e.g. for Keycloak trust configuration
String leafJwks = wallet.getWalletTlsCertificateJwks();
String caJwks = wallet.getWalletTlsCaCertificateJwks();
```

Add the exported PEM to your verifier's trust store instead of disabling TLS verification.

## Issuer metadata

When the wallet acts as the issuer, its metadata and HTTPS endpoints are:

```java
String issuerUrl = wallet.getIssuerUrl();                 // alias of getHttpsBaseUrl()
String issuerMetadataUrl = wallet.getIssuerMetadataUrl(); // https://host:tls-port/.well-known/jwt-vc-issuer
String trustListUrl = wallet.getHttpsTrustListUrl();
String statusListUrl = wallet.getHttpsStatusListUrl();
```
