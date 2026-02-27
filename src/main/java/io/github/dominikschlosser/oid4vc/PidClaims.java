package io.github.dominikschlosser.oid4vc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public class PidClaims {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Object> claims = new LinkedHashMap<>();

    public PidClaims givenName(String value) {
        claims.put("given_name", value);
        return this;
    }

    public PidClaims familyName(String value) {
        claims.put("family_name", value);
        return this;
    }

    public PidClaims birthdate(String value) {
        claims.put("birthdate", value);
        return this;
    }

    public PidClaims nationality(String value) {
        claims.put("nationality", value);
        return this;
    }

    public PidClaims birthCity(String value) {
        claims.put("birth_city", value);
        return this;
    }

    public PidClaims birthState(String value) {
        claims.put("birth_state", value);
        return this;
    }

    public PidClaims birthCountry(String value) {
        claims.put("birth_country", value);
        return this;
    }

    public PidClaims residentAddress(String value) {
        claims.put("resident_address", value);
        return this;
    }

    public PidClaims residentCity(String value) {
        claims.put("resident_city", value);
        return this;
    }

    public PidClaims residentPostalCode(String value) {
        claims.put("resident_postal_code", value);
        return this;
    }

    public PidClaims residentCountry(String value) {
        claims.put("resident_country", value);
        return this;
    }

    public PidClaims claim(String key, Object value) {
        claims.put(key, value);
        return this;
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(claims);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize PID claims", e);
        }
    }
}
