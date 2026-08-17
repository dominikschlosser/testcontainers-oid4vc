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
package io.github.dominikschlosser.eudi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Claim overrides for the wallet's generated mdoc PID, merged on top of the
 * default claim set. Since eudi-dev v1.22.0 the default PID follows the EUDI
 * PID Rulebook (doctype {@code eu.europa.ec.eudi.pid.1}), whose ISO 18013-5
 * attribute identifiers these builder methods mirror. Overrides replace a
 * claim wholesale, so the nested {@code place_of_birth} is set as a complete
 * object.
 *
 * <p>Elements outside the rulebook can be set with
 * {@link #claim(String, Object)}. The German PID's national elements (when
 * generating with {@link EudiWalletContainer#withPidType(String)}) live in
 * their own namespace, addressed with a {@code namespace:element} claim key,
 * e.g. {@code claim("eu.europa.ec.eudi.pid.de.1:birth_name", "GABLER")}.
 */
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

    public MdocPidClaims familyNameBirth(String value) {
        claims.put("family_name_birth", value);
        return this;
    }

    /**
     * Sex per ISO/IEC 5218: 0 unknown, 1 male, 2 female, 9 not applicable.
     */
    public MdocPidClaims sex(int value) {
        claims.put("sex", value);
        return this;
    }

    public MdocPidClaims placeOfBirth(String locality) {
        Map<String, String> place = new LinkedHashMap<>();
        place.put("locality", locality);
        claims.put("place_of_birth", place);
        return this;
    }

    public MdocPidClaims placeOfBirth(String locality, String country) {
        Map<String, String> place = new LinkedHashMap<>();
        place.put("locality", locality);
        place.put("country", country);
        claims.put("place_of_birth", place);
        return this;
    }

    /**
     * The holder's nationalities. The {@code nationality} element carries an
     * array of country codes.
     */
    public MdocPidClaims nationality(String... values) {
        claims.put("nationality", Arrays.asList(values));
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

    public MdocPidClaims personalAdministrativeNumber(String value) {
        claims.put("personal_administrative_number", value);
        return this;
    }

    public MdocPidClaims documentNumber(String value) {
        claims.put("document_number", value);
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
