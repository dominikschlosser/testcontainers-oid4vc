package io.github.dominikschlosser.oid4vc;

import com.authlete.sd.Disclosure;
import com.authlete.sd.SDJWT;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SdJwtCredentialBuilder {

    private final ECKey signingKey;
    private String vct;
    private String issuer;
    private Duration ttl = Duration.ofHours(24);
    private final Map<String, Object> flatClaims = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> objectClaims = new LinkedHashMap<>();
    private final Map<String, List<?>> arrayClaims = new LinkedHashMap<>();
    private JWK holderBindingKey;

    public SdJwtCredentialBuilder() {
        try {
            this.signingKey = new ECKeyGenerator(Curve.P_256).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to generate EC key", e);
        }
    }

    public SdJwtCredentialBuilder(ECKey signingKey) {
        this.signingKey = signingKey;
    }

    public SdJwtCredentialBuilder vct(String vct) {
        this.vct = vct;
        return this;
    }

    public SdJwtCredentialBuilder issuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    public SdJwtCredentialBuilder ttl(Duration ttl) {
        this.ttl = ttl;
        return this;
    }

    public SdJwtCredentialBuilder claim(String name, Object value) {
        this.flatClaims.put(name, value);
        return this;
    }

    public SdJwtCredentialBuilder objectClaim(String name, Map<String, Object> fields) {
        this.objectClaims.put(name, fields);
        return this;
    }

    public SdJwtCredentialBuilder arrayClaim(String name, List<?> elements) {
        this.arrayClaims.put(name, elements);
        return this;
    }

    public SdJwtCredentialBuilder holderBindingKey(JWK holderBindingKey) {
        this.holderBindingKey = holderBindingKey;
        return this;
    }

    public ECKey getSigningKey() {
        return signingKey;
    }

    public String build() {
        List<Disclosure> disclosures = new ArrayList<>();

        // Create disclosures for flat claims
        for (var entry : flatClaims.entrySet()) {
            disclosures.add(new Disclosure(entry.getKey(), entry.getValue()));
        }

        // Create disclosures for object claims (each sub-field is a separate disclosure)
        for (var entry : objectClaims.entrySet()) {
            for (var field : entry.getValue().entrySet()) {
                disclosures.add(new Disclosure(entry.getKey() + "." + field.getKey(), field.getValue()));
            }
        }

        // Create disclosures for array claims (per-element)
        for (var entry : arrayClaims.entrySet()) {
            for (Object element : entry.getValue()) {
                disclosures.add(new Disclosure(entry.getKey(), element));
            }
        }

        // Build JWT claims
        Instant now = Instant.now();
        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)));

        if (vct != null) {
            claimsBuilder.claim("vct", vct);
        }
        if (issuer != null) {
            claimsBuilder.issuer(issuer);
        }
        if (holderBindingKey != null) {
            claimsBuilder.claim("cnf", Map.of("jwk", holderBindingKey.toPublicJWK().toJSONObject()));
        }

        // Add _sd digests for disclosures
        List<String> sdDigests = new ArrayList<>();
        for (Disclosure disclosure : disclosures) {
            sdDigests.add(disclosure.digest());
        }
        if (!sdDigests.isEmpty()) {
            claimsBuilder.claim("_sd", sdDigests);
        }

        // Sign the JWT
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .keyID(signingKey.getKeyID())
                    .build();
            SignedJWT signedJWT = new SignedJWT(header, claimsBuilder.build());
            signedJWT.sign(new ECDSASigner(signingKey));

            // Assemble SD-JWT: jwt~disclosure1~disclosure2~...~
            SDJWT sdJwt = new SDJWT(signedJWT.serialize(), disclosures);
            return sdJwt.toString();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign SD-JWT", e);
        }
    }
}
