package io.github.dominikschlosser.oid4vc;

import java.util.Map;

public record Credential(String id, CredentialFormat format, String type, Map<String, Object> claims) {
}
