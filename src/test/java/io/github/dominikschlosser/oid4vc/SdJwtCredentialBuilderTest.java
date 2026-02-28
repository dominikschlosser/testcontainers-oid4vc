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

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SdJwtCredentialBuilderTest {

    @Test
    void buildProducesValidSdJwt() throws Exception {
        SdJwtCredentialBuilder builder = new SdJwtCredentialBuilder();

        String sdJwt = builder
                .vct("urn:example:test:1")
                .issuer("https://issuer.example.com")
                .claim("given_name", "Jane")
                .build();

        assertThat(sdJwt).contains("~");
        String jwtPart = sdJwt.split("~")[0];
        SignedJWT parsed = SignedJWT.parse(jwtPart);

        assertThat(parsed.getJWTClaimsSet().getStringClaim("vct")).isEqualTo("urn:example:test:1");
        assertThat(parsed.getJWTClaimsSet().getIssuer()).isEqualTo("https://issuer.example.com");
    }

    @Test
    void signatureIsVerifiableWithExposedKey() throws Exception {
        SdJwtCredentialBuilder builder = new SdJwtCredentialBuilder();

        String sdJwt = builder
                .vct("urn:example:test:1")
                .claim("name", "Test")
                .build();

        String jwtPart = sdJwt.split("~")[0];
        SignedJWT parsed = SignedJWT.parse(jwtPart);

        JWSVerifier verifier = new ECDSAVerifier(builder.getSigningKey().toPublicJWK());
        assertThat(parsed.verify(verifier)).isTrue();
    }

    @Test
    void acceptsCustomSigningKey() throws Exception {
        ECKey customKey = new ECKeyGenerator(Curve.P_256).keyID("custom-kid").generate();
        SdJwtCredentialBuilder builder = new SdJwtCredentialBuilder(customKey);

        String sdJwt = builder
                .vct("urn:example:test:1")
                .claim("name", "Test")
                .build();

        String jwtPart = sdJwt.split("~")[0];
        SignedJWT parsed = SignedJWT.parse(jwtPart);
        assertThat(parsed.getHeader().getKeyID()).isEqualTo("custom-kid");

        JWSVerifier verifier = new ECDSAVerifier(customKey.toPublicJWK());
        assertThat(parsed.verify(verifier)).isTrue();
    }

    @Test
    void flatClaimsProduceDisclosures() {
        String sdJwt = new SdJwtCredentialBuilder()
                .claim("given_name", "Jane")
                .claim("family_name", "Doe")
                .build();

        // JWT ~ disclosure1 ~ disclosure2 ~ (trailing)
        String[] parts = sdJwt.split("~");
        // At least the JWT + 2 disclosures
        assertThat(parts.length).isGreaterThanOrEqualTo(3);
    }

    @Test
    void objectClaimsProduceDisclosuresPerField() {
        String sdJwt = new SdJwtCredentialBuilder()
                .objectClaim("address", Map.of("street", "123 Main St", "city", "Berlin"))
                .build();

        String[] parts = sdJwt.split("~");
        // JWT + 2 disclosures (street + city)
        assertThat(parts.length).isGreaterThanOrEqualTo(3);
    }

    @Test
    void arrayClaimsProduceDisclosuresPerElement() {
        String sdJwt = new SdJwtCredentialBuilder()
                .arrayClaim("nationalities", List.of("DE", "US", "FR"))
                .build();

        String[] parts = sdJwt.split("~");
        // JWT + 3 disclosures
        assertThat(parts.length).isGreaterThanOrEqualTo(4);
    }

    @Test
    void ttlIsReflectedInExpiration() throws Exception {
        String sdJwt = new SdJwtCredentialBuilder()
                .ttl(Duration.ofMinutes(5))
                .claim("name", "Test")
                .build();

        String jwtPart = sdJwt.split("~")[0];
        SignedJWT parsed = SignedJWT.parse(jwtPart);

        long iat = parsed.getJWTClaimsSet().getIssueTime().getTime();
        long exp = parsed.getJWTClaimsSet().getExpirationTime().getTime();
        long diffMinutes = (exp - iat) / 1000 / 60;
        assertThat(diffMinutes).isEqualTo(5);
    }

    @Test
    void buildWithoutClaimsProducesJwtOnly() throws Exception {
        String sdJwt = new SdJwtCredentialBuilder()
                .vct("urn:example:empty:1")
                .build();

        // Should still be parseable
        String jwtPart = sdJwt.split("~")[0];
        SignedJWT parsed = SignedJWT.parse(jwtPart);
        assertThat(parsed.getJWTClaimsSet().getStringClaim("vct")).isEqualTo("urn:example:empty:1");
        // No _sd claim when there are no disclosures
        assertThat(parsed.getJWTClaimsSet().getClaim("_sd")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void holderBindingKeyAddsCnfClaim() throws Exception {
        ECKey holderKey = new ECKeyGenerator(Curve.P_256).generate();
        String sdJwt = new SdJwtCredentialBuilder()
                .vct("urn:example:test:1")
                .holderBindingKey(holderKey)
                .claim("name", "Test")
                .build();

        String jwtPart = sdJwt.split("~")[0];
        SignedJWT parsed = SignedJWT.parse(jwtPart);

        Map<String, Object> cnf = (Map<String, Object>) parsed.getJWTClaimsSet().getClaim("cnf");
        assertThat(cnf).isNotNull();
        Map<String, Object> jwk = (Map<String, Object>) cnf.get("jwk");
        assertThat(jwk).isNotNull();
        assertThat(jwk.get("kty")).isEqualTo("EC");
        assertThat(jwk.get("crv")).isEqualTo("P-256");
        // Should only contain public key components, no private key 'd'
        assertThat(jwk).doesNotContainKey("d");
        assertThat(jwk).containsKeys("x", "y");
    }

    @Test
    void autoGeneratesKeyWhenNoneProvided() {
        SdJwtCredentialBuilder builder = new SdJwtCredentialBuilder();
        ECKey key = builder.getSigningKey();

        assertThat(key).isNotNull();
        assertThat(key.getCurve()).isEqualTo(Curve.P_256);
        assertThat(key.isPrivate()).isTrue();
    }
}
