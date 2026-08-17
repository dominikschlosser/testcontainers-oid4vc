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
import java.util.List;
import java.util.Map;

/**
 * Claim overrides for the wallet's generated SD-JWT PID, merged on top of the
 * default claim set. Since eudi-dev v1.22.0 the default PID follows the EUDI
 * PID Rulebook (vct {@code urn:eudi:pid:1}), whose attributes these builder
 * methods mirror. Overrides replace a claim wholesale, so nested claims like
 * {@code address} and {@code place_of_birth} are set as complete objects.
 *
 * <p>Claims outside the rulebook — including the German PID's national
 * additions when generating with
 * {@link EudiWalletContainer#withPidType(String)} — can be set with
 * {@link #claim(String, Object)}.
 */
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

    public SdJwtPidClaims birthFamilyName(String value) {
        claims.put("birth_family_name", value);
        return this;
    }

    /**
     * Sex per ISO/IEC 5218: 0 unknown, 1 male, 2 female, 9 not applicable.
     */
    public SdJwtPidClaims sex(int value) {
        claims.put("sex", value);
        return this;
    }

    public SdJwtPidClaims placeOfBirth(String locality) {
        Map<String, String> place = new LinkedHashMap<>();
        place.put("locality", locality);
        claims.put("place_of_birth", place);
        return this;
    }

    public SdJwtPidClaims placeOfBirth(String locality, String country) {
        Map<String, String> place = new LinkedHashMap<>();
        place.put("locality", locality);
        place.put("country", country);
        claims.put("place_of_birth", place);
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

    /**
     * The full rulebook address: the house number is its own subclaim, kept
     * out of {@code street_address}.
     */
    public SdJwtPidClaims address(String streetAddress, String houseNumber, String postalCode, String locality,
                                  String region, String country) {
        Map<String, String> addr = new LinkedHashMap<>();
        addr.put("street_address", streetAddress);
        addr.put("house_number", houseNumber);
        addr.put("postal_code", postalCode);
        addr.put("locality", locality);
        addr.put("region", region);
        addr.put("country", country);
        claims.put("address", addr);
        return this;
    }

    public SdJwtPidClaims personalAdministrativeNumber(String value) {
        claims.put("personal_administrative_number", value);
        return this;
    }

    public SdJwtPidClaims documentNumber(String value) {
        claims.put("document_number", value);
        return this;
    }

    public SdJwtPidClaims dateOfIssuance(String value) {
        claims.put("date_of_issuance", value);
        return this;
    }

    public SdJwtPidClaims dateOfExpiry(String value) {
        claims.put("date_of_expiry", value);
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
