# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.2.0] - 2026-08-17

### Changed

- pinned the default wallet image to `ghcr.io/dominikschlosser/eudi-dev:v1.24.1`
- the default PID claim set follows the EUDI PID Rulebook since eudi-dev v1.22.0, and the `SdJwtPidClaims` / `MdocPidClaims` override builders follow it: `birthFamilyName` / `familyNameBirth` (`birth_family_name` / `family_name_birth`), `sex(int)`, a nested `placeOfBirth(locality[, country])`, `personalAdministrativeNumber`, `documentNumber`, `dateOfIssuance` / `dateOfExpiry` (SD-JWT), an SD-JWT `address` overload with the rulebook's separate `house_number`, and an array-valued mdoc `nationality(...)`. The methods for claims the rulebook dropped (`gender`, the flat `birthPlace` / `birthCountry` / `birthState` / `birthCity`, `givenNameBirth`, the `age*` thresholds, the SD-JWT `issuanceDate` / `expiryDate` names) are gone — for national additions such as the German PID's, use `claim(name, value)`
- since eudi-dev v1.22.0 the pre-defined `pid-sdjwt` / `pid-mdoc` templates back the default PID, and `german-pid-sdjwt` / `german-pid-mdoc` carry the German PID type — issuing from `german-pid-sdjwt` now yields `vct urn:eudi:pid:de:1`

### Added

- OpenID4VCI feature level (eudi-dev v1.23.0): `withVciVersion(VciVersion.V1_1)` starts the wallet at the 1.1 draft level, which enables interactive authorization (OpenID4VCI 1.1 §6) — against an issuer publishing an `authorization_challenge_endpoint`, the wallet redeems an authorization-code offer by presenting a credential it holds instead of a browser redirect, with no redirect URI needed. The container's built-in demo issuer implements the issuer half at the same level (`POST /issuer/api/offers?grant=authorization_code&authorization=presentation`)
- runtime conformance setters on `WalletClient` (`PUT`/`DELETE /api/config/conformance`): `setValidationMode(ValidationMode)`, `setRequireHaip(boolean)`, `setRequireEncryptedRequest(boolean)`, `setVciVersion(VciVersion)`, and `resetConformance()` to restore the startup values — no container restart needed
- `withPidType(vct)` selects the generated default PID's type and claim set (eudi-dev v1.22.0): `urn:eudi:pid:de:1` for the German PID with its national additions, combinable with `withPidClaims(...)`
- `WalletConfig` gained `vciVersion()`, `requireHaipIssuance()` and `forceClientAttestation()`

## [2.1.0] - 2026-08-12

### Changed

- pinned the default wallet image to `ghcr.io/dominikschlosser/eudi-dev:v1.21.6`
- the default PID is the country-independent EUDI PID since eudi-dev v1.19.3: the SD-JWT `vct` is `urn:eudi:pid:1` (was `urn:eudi:pid:de:1`) and every mdoc element sits in `eu.europa.ec.eudi.pid.1` with no national namespace. Verifier configurations and DCQL queries matching on the old `vct` have to be updated

### Fixed

- `SdJwtCredentialBuilder` emits RFC 9901-conformant selective disclosure now: object claims nest their sub-field digests in an `_sd` array inside the object (instead of literal dotted top-level names like `address.city`), and array claims disclose per element through `{"...": <digest>}` placeholders (instead of several disclosures sharing one claim name, which resolves to a duplicate claim). eudi-dev enforces RFC 9901 §7.1 on import since v1.19.18 and rejects the old shapes
- `getTrustLists()` no longer fails on a trust-list profile without attestations, such as the wallet-provider list eudi-dev serves since v1.19.1

## [2.0.1] - 2026-08-04

### Changed

- pinned the default wallet image to `ghcr.io/dominikschlosser/eudi-dev:v1.18.4`
- consent is per channel since eudi-dev v1.18.0: `acceptCredentialOffer(...)` and `acceptPresentationRequest(...)` accept even on a wallet started `withoutAutoAccept()`, because the API call is the caller's own consent
- `WalletConfig` gained `version()` and `tlsListener()` (false when an external TLS terminator serves the issuer origin, so the built-in HTTPS listener is disabled)

### Added

- interactive consent submissions: `submitCredentialOfferForConsent(uri)` / `submitPresentationRequestForConsent(uri)` submit through the interactive channel so a wallet without auto-accept raises a consent request, and `awaitPendingRequest(timeout)` waits for it. Both submissions return a `CompletableFuture` because the wallet holds them open until the request is decided

## [2.0.0] - 2026-08-03

### Changed

- **Renamed to `testcontainers-eudi`**, following the wrapped tool's rename from `oid4vc-dev` to [`eudi-dev`](https://github.com/dominikschlosser/eudi-dev). The Maven artifact is `io.github.dominikschlosser:testcontainers-eudi`, the Java package is `io.github.dominikschlosser.eudi`, and the container class is `EudiWalletContainer`. Versions up to 1.6.0 remain available as `testcontainers-oid4vc`
- pinned the default wallet image to `ghcr.io/dominikschlosser/eudi-dev:v1.16.1`
- container readiness now waits on `GET /api/version` instead of `/`
- TLS certificate exports now use the wallet management HTTP API (`GET /api/certificates/tls|ca`) instead of `exec`ing the CLI in the container
- `Credential` now carries the raw credential (`raw()`) and its status-list metadata (`status()`)
- trust-list JWT payloads are nested under a top-level `LoTE` object since eudi-dev v1.7.1 (spec-conformant shape)

### Added

- credential issuance via the wallet's issuer key: `WalletClient.issueCredential(IssueRequest)` mirrors `eudi issue sdjwt|jwt|mdoc --wallet`, with support for claims, templates, `always_disclosed` SD-JWT claims, per-attribute mDoc namespaces (`namespace:element` claim keys), expiry/not-before, status-list references, and trust profiles
- credential template management: `getTemplates()`, `getTemplate(name)`, `saveTemplate(CredentialTemplate)`, `deleteTemplate(name)`, plus `EudiWalletContainer.withTemplatesDirectory(Path)` to ship a folder of template JSON files into the container (`--templates-dir`)
- credential status handling: `getCredentialStatus(id)` resolves the live status from the wallet's own status list or by fetching an external one; credential summaries include a typed `CredentialStatusInfo`
- consent handling for wallets without auto-accept: `getPendingRequests()`, `approveRequest(id[, selectedClaims])`, `denyRequest(id)`
- wallet introspection: `getConfig()` returns the instance document (`GET /api/config`), `getActivityLog()` / `clearActivityLog()` expose the wallet activity log
- single-credential fetch (`getCredential(id)`) and bulk delete (`deleteAllCredentials()`)
- JWKS certificate exports (`getWalletTlsCertificateJwks()`, `getWalletTlsCaCertificateJwks()`), e.g. for Keycloak trust configuration
- credential offer endpoint URLs (`getCredentialOfferUrl()`, `getHttpsCredentialOfferUrl()`) for delivering offers by plain web URL instead of the `openid-credential-offer://` scheme
- new container options: `withHaip()` (enforce HAIP 1.0), `withStrictValidation()` (strict wallet validation mode), `withVciClientId(...)` / `withVciRedirectUri(...)` (OID4VCI authorization-code flows)

### Removed

- the v1.5.0 issuer-focused certificate aliases (`getIssuerTlsCertificatePem`, `exportIssuerTlsCertificate`, `getIssuerTlsCaCertificatePem`, `exportIssuerTlsCaCertificate`) — use the `WalletTls*` methods
- the `withStatusListBaseUrl(...)` compatibility alias — use `withBaseUrl(...)`

## [1.6.0] - 2026-03-22

### Added

- trust-list discovery support for `oid4vc-dev v1.7.0`, including `/api/trustlists`, `/api/trustlists/{id}`, and legacy `vct` / `doctype` selectors
- typed `TrustListIndexEntry` and `TrustListAttestation` records for wallet trust-list discovery results
- URL resolution helpers for trust-list discovery entries so Testcontainers callers can resolve relative wallet `path` values against mapped host URLs

### Changed

- pinned the default wallet image to `ghcr.io/dominikschlosser/oid4vc-dev:v1.7.0`
- updated README usage examples to document trust-list discovery and mapped-URL resolution
