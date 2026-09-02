# Configuration

## Startup options

```java
EudiWalletContainer wallet = new EudiWalletContainer()
    .withStatusList()                                // enable the status list endpoint, issued credentials become revocable
    .withBaseUrl("http://wallet.example.test:8085")  // advertised HTTP base URL, the HTTPS host follows it
    .withSessionTranscript("iso")                    // mdoc session transcript: "oid4vp" (default) or "iso"
    .withVciClientId("my-wallet")                    // client id for OID4VCI authorization-code flows
    .withVciRedirectUri("http://localhost:8085/callback")
    .withPidType("urn:eudi:pid:de:1")                // generate the German PID instead of the country-independent one
    .withoutAutoAccept()                             // wait for explicit consent instead of accepting everything
    .withoutDefaultPid();                            // start without the default PID credential
```

`withTemplatesDirectory(Path)` ships a folder of credential templates into the container, see [Credentials](credentials.md). `withHostAccess()` lets the wallet reach services on the Docker host, see [Networking](networking.md).

## Conformance settings

Four settings control how strictly the wallet plays the protocol. Each has a startup option and a runtime setter, so a single container can serve tests with different requirements:

| Setting | Startup | Runtime |
|---|---|---|
| Validation mode | `withStrictValidation()` | `setValidationMode(mode)` |
| HAIP 1.0 enforcement | `withHaip()` | `setRequireHaip(flag)` |
| Encrypted request objects | `withRequireEncryptedRequest()` | `setRequireEncryptedRequest(flag)` |
| OpenID4VCI feature level | `withVciVersion(version)` | `setVciVersion(version)` |

In the default `debug` mode the wallet tolerates spec violations by the issuer or verifier under test and logs them. In `strict` mode the violation fails the flow. `withHaip()` enforces HAIP 1.0 on top (x509_hash client id prefix, `direct_post.jwt`, DCQL, JAR, ES256), and `withRequireEncryptedRequest()` makes the wallet advertise an encryption key in its `wallet_metadata` and demand JWE request objects.

`withVciVersion(VciVersion.V1_1)` moves the wallet to the OpenID4VCI 1.1 draft level, which enables interactive authorization: against an issuer publishing an `authorization_challenge_endpoint`, the wallet redeems an authorization-code offer by presenting a credential it holds instead of going through a browser redirect. See [Flows](flows.md#presentation-during-issuance-openid4vci-11) for the flow.

Runtime changes hold until the container restarts. `resetConformance()` restores the startup values:

```java
WalletClient client = wallet.client();
client.setValidationMode(ValidationMode.STRICT);
// run the strict part of the test
client.resetConformance();
```

The preferred credential format works the same way: `withPreferredFormat(CredentialFormat.SD_JWT)` at startup, `setPreferredFormat(format)` and `clearPreferredFormat()` at runtime.

## Custom PID claims

Use a format-specific claims builder:

```java
// SD-JWT format
EudiWalletContainer wallet = new EudiWalletContainer()
    .withPidClaims(new SdJwtPidClaims()
        .givenName("Jane")
        .familyName("Doe")
        .birthdate("1990-01-15")
        .nationalities("DE"));

// mdoc format
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

`withPidType(...)` selects another PID type. `urn:eudi:pid:de:1` generates the German PID with its national additions, which are set through the generic `claim(name, value)` escape hatch:

```java
EudiWalletContainer wallet = new EudiWalletContainer()
    .withPidType("urn:eudi:pid:de:1")
    .withPidClaims(new SdJwtPidClaims()
        .givenName("Jane")
        .claim("birth_name", "Doe"));
```

## Error injection

Make the wallet answer the next presentation request with a specific OAuth error, for testing your verifier's error handling. The override is one-shot and applies to the next request only:

```java
client.setNextError("access_denied", "User denied consent");
client.clearNextError();
```
