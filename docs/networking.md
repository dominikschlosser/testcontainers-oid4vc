# Networking and URLs

## Host access

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

## Base URL

`withBaseUrl(...)` sets the base URL the wallet advertises in its metadata and offers. Use it when the counterpart must see a fixed address rather than the mapped Testcontainers one. The HTTPS endpoints reuse the same host:

```java
EudiWalletContainer wallet = new EudiWalletContainer()
    .withBaseUrl("http://wallet.example.test:8085");
```

## Endpoint URLs

The container resolves every wallet endpoint to its mapped host and port:

```java
wallet.getBaseUrl();                 // http://host:port
wallet.getHttpsBaseUrl();            // https://host:tls-port
wallet.getAuthorizeUrl();            // http://host:port/authorize
wallet.getHttpsAuthorizeUrl();       // https://host:tls-port/authorize
wallet.getCredentialOfferUrl();      // http://host:port/credential-offer
wallet.getHttpsCredentialOfferUrl(); // https://host:tls-port/credential-offer
wallet.getCredentialsUrl();          // http://host:port/api/credentials
wallet.getTemplatesUrl();            // http://host:port/api/templates
wallet.getStatusListUrl();           // http://host:port/api/statuslist
wallet.getHttpsStatusListUrl();      // https://host:tls-port/api/statuslist
wallet.getIssuerUrl();               // alias of getHttpsBaseUrl()
wallet.getIssuerMetadataUrl();       // https://host:tls-port/.well-known/jwt-vc-issuer
```

Trust list URLs, in HTTP and HTTPS form:

```java
wallet.getTrustListUrl();            // http://host:port/api/trustlist
wallet.getTrustListsUrl();           // http://host:port/api/trustlists
wallet.getTrustListUrl("local");     // http://host:port/api/trustlists/local
wallet.getTrustListUrlForVct("urn:example:my-credential:1");
wallet.getTrustListUrlForDocType("org.iso.23220.photoid.1");
wallet.getHttpsTrustListUrl();
wallet.getHttpsTrustListsUrl();
wallet.getHttpsTrustListUrl("local");
wallet.getHttpsTrustListUrlForVct("urn:example:my-credential:1");
wallet.getHttpsTrustListUrlForDocType("org.iso.23220.photoid.1");
```

For trust-list entries read from the `/api/trustlists` discovery endpoint, `wallet.resolveTrustListUrl(entry)` and `wallet.resolveHttpsTrustListUrl(entry)` resolve an entry's relative path against the mapped wallet URL. See [Trust](trust.md).
