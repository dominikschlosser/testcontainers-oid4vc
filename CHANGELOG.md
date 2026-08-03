# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- **Renamed to `testcontainers-eudi`**, following the wrapped tool's rename from `oid4vc-dev` to [`eudi-dev`](https://github.com/dominikschlosser/eudi-dev). The Maven artifact is `io.github.dominikschlosser:testcontainers-eudi`, the Java package is `io.github.dominikschlosser.eudi`, and the container class is `EudiWalletContainer`. Versions up to 1.6.0 remain available as `testcontainers-oid4vc`
- pinned the default wallet image to `ghcr.io/dominikschlosser/eudi-dev:v1.16.1`
- container readiness now waits on `GET /api/version` instead of `/`
- TLS certificate exports now use the wallet management HTTP API (`GET /api/certificates/tls|ca`) instead of `exec`ing the CLI in the container
- `Credential` now carries the raw credential (`raw()`) and its status-list metadata (`status()`)
- trust-list JWT payloads are nested under a top-level `LoTE` object since eudi-dev v1.7.1 (spec-conformant shape)

### Added

- credential issuance via the wallet's issuer key: `WalletClient.issueCredential(IssueRequest)` mirrors `eudi issue sdjwt|jwt|mdoc --wallet`, with support for claims, templates, `always_disclosed` SD-JWT claims, per-attribute mDoc namespaces (`namespace:element` claim keys), expiry/not-before, status-list references, and trust profiles
- credential template management: `getTemplates()`, `getTemplate(name)`, `saveTemplate(CredentialTemplate)`, `deleteTemplate(name)`, plus `EudiWalletContainer.withTemplatesDirectory(Path)` to ship a folder of template JSON files into the container (`--templates-dir`). The pre-defined `german-pid-sdjwt` / `german-pid-mdoc` templates back the default PID
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
