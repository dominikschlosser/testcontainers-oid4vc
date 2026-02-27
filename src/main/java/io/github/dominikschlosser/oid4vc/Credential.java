package io.github.dominikschlosser.oid4vc;

import java.util.Map;

public record Credential(String id, String format, String type, Map<String, Object> claims) {
}
