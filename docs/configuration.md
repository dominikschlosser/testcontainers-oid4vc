# Configuration

## Startup options

The container supports fluent configuration:

```java
EudiWalletContainer wallet = new EudiWalletContainer()
    .withStatusList()                                // enable the status list endpoint
    .withBaseUrl("http://wallet.example.test:8085")  // advertised HTTP base URL, the HTTPS host follows it
    .withPreferredFormat(CredentialFormat.SD_JWT)    // preferred credential format
    .withSessionTranscript("iso")                    // mdoc session transcript: "oid4vp" (default) or "iso"
    .withRequireEncryptedRequest()                   // require encrypted request objects (wallet_metadata)
    .withHaip()                                      // enforce HAIP 1.0 compliance
    .withStrictValidation()                          // fail flows on spec violations instead of tolerating them
    .withVciVersion(VciVersion.V1_1)                 // OpenID4VCI feature level, enables interactive authorization
    .withVciClientId("my-wallet")                    // client id for OID4VCI authorization-code flows
    .withVciRedirectUri("http://localhost:8085/callback")
    .withPidType("urn:eudi:pid:de:1")                // generate the German PID instead of the country-independent one
    .withoutAutoAccept()                             // wait for explicit consent instead of accepting everything
    .withoutDefaultPid();                            // start without the default PID credential
```

`withVciVersion(VciVersion.V1_1)` starts the wallet at the OpenID4VCI 1.1 draft level. Against an issuer publishing an `authorization_challenge_endpoint`, the wallet then redeems an authorization-code offer by presenting a credential it holds instead of going through a browser redirect, with no redirect URI needed. The container's built-in demo issuer implements the issuer half at the same level.

## Runtime conformance switches

Validation mode, HAIP, the encrypted-request requirement and the OpenID4VCI feature level are also changeable at runtime, without restarting the container:

```java
WalletClient client = wallet.client();
client.setValidationMode(ValidationMode.STRICT);
client.setRequireHaip(true);
client.setRequireEncryptedRequest(true);
client.setVciVersion(VciVersion.V1_1);
client.resetConformance();      // restore the settings the wallet started with
```

The preferred credential format is switchable at runtime too:

```java
client.setPreferredFormat(CredentialFormat.MSO_MDOC);
client.clearPreferredFormat();
```

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
