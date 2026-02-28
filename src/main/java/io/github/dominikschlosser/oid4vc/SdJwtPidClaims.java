/*
 * Copyright 2026 Dominik Schlosser
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.dominikschlosser.oid4vc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SdJwtPidClaims implements PidClaims {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Object> claims = new LinkedHashMap<>();

    public SdJwtPidClaims givenName(String value) {
        claims.put("given_name", value);
        return this;
    }

    public SdJwtPidClaims familyName(String value) {
        claims.put("family_name", value);
        return this;
    }

    public SdJwtPidClaims birthdate(String value) {
        claims.put("birthdate", value);
        return this;
    }

    public SdJwtPidClaims nationalities(String... values) {
        claims.put("nationalities", Arrays.asList(values));
        return this;
    }

    public SdJwtPidClaims nationalities(List<String> values) {
        claims.put("nationalities", values);
        return this;
    }

    public SdJwtPidClaims address(String streetAddress, String locality, String postalCode, String country) {
        Map<String, String> addr = new LinkedHashMap<>();
        addr.put("street_address", streetAddress);
        addr.put("locality", locality);
        addr.put("postal_code", postalCode);
        addr.put("country", country);
        claims.put("address", addr);
        return this;
    }

    public SdJwtPidClaims address(String streetAddress, String locality, String postalCode, String country, String region) {
        Map<String, String> addr = new LinkedHashMap<>();
        addr.put("street_address", streetAddress);
        addr.put("locality", locality);
        addr.put("postal_code", postalCode);
        addr.put("country", country);
        addr.put("region", region);
        claims.put("address", addr);
        return this;
    }

    public SdJwtPidClaims gender(String value) {
        claims.put("gender", value);
        return this;
    }

    public SdJwtPidClaims birthPlace(String value) {
        claims.put("birth_place", value);
        return this;
    }

    public SdJwtPidClaims birthCountry(String value) {
        claims.put("birth_country", value);
        return this;
    }

    public SdJwtPidClaims birthState(String value) {
        claims.put("birth_state", value);
        return this;
    }

    public SdJwtPidClaims birthCity(String value) {
        claims.put("birth_city", value);
        return this;
    }

    public SdJwtPidClaims familyNameBirth(String value) {
        claims.put("family_name_birth", value);
        return this;
    }

    public SdJwtPidClaims givenNameBirth(String value) {
        claims.put("given_name_birth", value);
        return this;
    }

    public SdJwtPidClaims ageOver18(boolean value) {
        claims.put("age_over_18", value);
        return this;
    }

    public SdJwtPidClaims ageInYears(int value) {
        claims.put("age_in_years", value);
        return this;
    }

    public SdJwtPidClaims ageBirthYear(int value) {
        claims.put("age_birth_year", value);
        return this;
    }

    public SdJwtPidClaims issuanceDate(String value) {
        claims.put("issuance_date", value);
        return this;
    }

    public SdJwtPidClaims expiryDate(String value) {
        claims.put("expiry_date", value);
        return this;
    }

    public SdJwtPidClaims issuingAuthority(String value) {
        claims.put("issuing_authority", value);
        return this;
    }

    public SdJwtPidClaims issuingCountry(String value) {
        claims.put("issuing_country", value);
        return this;
    }

    public SdJwtPidClaims issuingJurisdiction(String value) {
        claims.put("issuing_jurisdiction", value);
        return this;
    }

    public SdJwtPidClaims claim(String key, Object value) {
        claims.put(key, value);
        return this;
    }

    @Override
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(claims);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize PID claims", e);
        }
    }
}
