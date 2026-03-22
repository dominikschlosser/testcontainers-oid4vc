# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.6.0] - 2026-03-22

### Added

- trust-list discovery support for `oid4vc-dev v1.7.0`, including `/api/trustlists`, `/api/trustlists/{id}`, and legacy `vct` / `doctype` selectors
- typed `TrustListIndexEntry` and `TrustListAttestation` records for wallet trust-list discovery results
- URL resolution helpers for trust-list discovery entries so Testcontainers callers can resolve relative wallet `path` values against mapped host URLs

### Changed

- pinned the default wallet image to `ghcr.io/dominikschlosser/oid4vc-dev:v1.7.0`
- updated README usage examples to document trust-list discovery and mapped-URL resolution
