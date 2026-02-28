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

import java.util.LinkedHashMap;
import java.util.Map;

public class MdocPidClaims implements PidClaims {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Object> claims = new LinkedHashMap<>();

    public MdocPidClaims givenName(String value) {
        claims.put("given_name", value);
        return this;
    }

    public MdocPidClaims familyName(String value) {
        claims.put("family_name", value);
        return this;
    }

    public MdocPidClaims birthDate(String value) {
        claims.put("birth_date", value);
        return this;
    }

    public MdocPidClaims nationality(String value) {
        claims.put("nationality", value);
        return this;
    }

    public MdocPidClaims residentAddress(String value) {
        claims.put("resident_address", value);
        return this;
    }

    public MdocPidClaims residentCountry(String value) {
        claims.put("resident_country", value);
        return this;
    }

    public MdocPidClaims residentState(String value) {
        claims.put("resident_state", value);
        return this;
    }

    public MdocPidClaims residentCity(String value) {
        claims.put("resident_city", value);
        return this;
    }

    public MdocPidClaims residentPostalCode(String value) {
        claims.put("resident_postal_code", value);
        return this;
    }

    public MdocPidClaims residentStreet(String value) {
        claims.put("resident_street", value);
        return this;
    }

    public MdocPidClaims residentHouseNumber(String value) {
        claims.put("resident_house_number", value);
        return this;
    }

    public MdocPidClaims gender(String value) {
        claims.put("gender", value);
        return this;
    }

    public MdocPidClaims birthPlace(String value) {
        claims.put("birth_place", value);
        return this;
    }

    public MdocPidClaims birthCountry(String value) {
        claims.put("birth_country", value);
        return this;
    }

    public MdocPidClaims birthState(String value) {
        claims.put("birth_state", value);
        return this;
    }

    public MdocPidClaims birthCity(String value) {
        claims.put("birth_city", value);
        return this;
    }

    public MdocPidClaims familyNameBirth(String value) {
        claims.put("family_name_birth", value);
        return this;
    }

    public MdocPidClaims givenNameBirth(String value) {
        claims.put("given_name_birth", value);
        return this;
    }

    public MdocPidClaims ageOver18(boolean value) {
        claims.put("age_over_18", value);
        return this;
    }

    public MdocPidClaims ageInYears(int value) {
        claims.put("age_in_years", value);
        return this;
    }

    public MdocPidClaims ageBirthYear(int value) {
        claims.put("age_birth_year", value);
        return this;
    }

    public MdocPidClaims issuanceDate(String value) {
        claims.put("issuance_date", value);
        return this;
    }

    public MdocPidClaims expiryDate(String value) {
        claims.put("expiry_date", value);
        return this;
    }

    public MdocPidClaims issuingAuthority(String value) {
        claims.put("issuing_authority", value);
        return this;
    }

    public MdocPidClaims issuingCountry(String value) {
        claims.put("issuing_country", value);
        return this;
    }

    public MdocPidClaims issuingJurisdiction(String value) {
        claims.put("issuing_jurisdiction", value);
        return this;
    }

    public MdocPidClaims claim(String key, Object value) {
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
