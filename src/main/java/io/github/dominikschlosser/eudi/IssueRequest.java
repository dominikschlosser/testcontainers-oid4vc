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

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request for {@link WalletClient#issueCredential(IssueRequest)}: issues a
 * credential with the wallet's issuer key and imports it into the wallet,
 * mirroring {@code eudi issue sdjwt|jwt|mdoc --wallet}.
 *
 * <pre>{@code
 * // From a pre-defined template, overriding individual claims
 * Credential pid = wallet.client().issueCredential(IssueRequest.fromTemplate("german-pid-sdjwt")
 *         .claim("given_name", "Jane"));
 *
 * // Free-form SD-JWT credential
 * Credential cred = wallet.client().issueCredential(IssueRequest.sdJwt("urn:example:my-credential:1")
 *         .claim("given_name", "Jane")
 *         .alwaysDisclosed("given_name")
 *         .ttl(Duration.ofHours(1)));
 * }</pre>
 */
public class IssueRequest {

    private String format;
    private String template;
    private final Map<String, Object> claims = new LinkedHashMap<>();
    private boolean pid;
    private final List<String> omit = new ArrayList<>();
    private final List<String> alwaysDisclosed = new ArrayList<>();
    private String saveAsTemplate;
    private String vct;
    private String docType;
    private String namespace;
    private String exp;
    private String nbf;
    private String statusListUri;
    private Integer statusListIdx;
    private String trustProfile;

    private IssueRequest() {
    }

    /** Issues from a template (pre-defined or user-saved), e.g. {@code german-pid-sdjwt}. */
    public static IssueRequest fromTemplate(String templateName) {
        IssueRequest request = new IssueRequest();
        request.template = templateName;
        return request;
    }

    /** Issues an SD-JWT credential with the given VCT. */
    public static IssueRequest sdJwt(String vct) {
        IssueRequest request = new IssueRequest();
        request.format = "sdjwt";
        request.vct = vct;
        return request;
    }

    /** Issues a JWT VC credential with the given VCT. */
    public static IssueRequest jwtVc(String vct) {
        IssueRequest request = new IssueRequest();
        request.format = "jwt";
        request.vct = vct;
        return request;
    }

    /** Issues an mDoc credential with the given doc type. */
    public static IssueRequest mdoc(String docType) {
        IssueRequest request = new IssueRequest();
        request.format = "mdoc";
        request.docType = docType;
        return request;
    }

    /** Issues the default EUDI PID claim set. */
    public static IssueRequest pid(CredentialFormat format) {
        IssueRequest request = new IssueRequest();
        request.format = format.getWireValue();
        request.pid = true;
        return request;
    }

    public IssueRequest format(CredentialFormat format) {
        this.format = format.getWireValue();
        return this;
    }

    /**
     * Adds a single claim, overriding the template's value for the same key.
     * mDoc claims may use {@code namespace:element} keys to place single
     * attributes in their own namespace.
     */
    public IssueRequest claim(String name, Object value) {
        this.claims.put(name, value);
        return this;
    }

    public IssueRequest claims(Map<String, Object> claims) {
        this.claims.putAll(claims);
        return this;
    }

    /** Omits the named claims from the template or default claim set. */
    public IssueRequest omit(String... claimNames) {
        this.omit.addAll(List.of(claimNames));
        return this;
    }

    /**
     * Embeds the named SD-JWT claims plainly in the signed payload so they are
     * always visible and cannot be withheld during presentation. Nested
     * subclaims use dotted paths ({@code address.country}). Rejected for mDoc.
     */
    public IssueRequest alwaysDisclosed(String... claimNames) {
        this.alwaysDisclosed.addAll(List.of(claimNames));
        return this;
    }

    /** Saves the issued parameters as a reusable template. */
    public IssueRequest saveAsTemplate(String templateName) {
        this.saveAsTemplate = templateName;
        return this;
    }

    public IssueRequest vct(String vct) {
        this.vct = vct;
        return this;
    }

    public IssueRequest docType(String docType) {
        this.docType = docType;
        return this;
    }

    /** Default namespace for mDoc claims without a {@code namespace:} prefix. */
    public IssueRequest namespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public IssueRequest ttl(Duration ttl) {
        this.exp = ttl.toSeconds() + "s";
        return this;
    }

    /** Expiry as a Go duration string, e.g. {@code 24h}. */
    public IssueRequest exp(String goDuration) {
        this.exp = goDuration;
        return this;
    }

    /** Not-before as a Go duration string or RFC 3339 timestamp. */
    public IssueRequest notBefore(String nbf) {
        this.nbf = nbf;
        return this;
    }

    /** References an external status list instead of the wallet's own. */
    public IssueRequest statusList(String uri, int idx) {
        this.statusListUri = uri;
        this.statusListIdx = idx;
        return this;
    }

    public IssueRequest trustProfile(String trustProfile) {
        this.trustProfile = trustProfile;
        return this;
    }

    Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        if (format != null) {
            body.put("format", format);
        }
        if (template != null) {
            body.put("template", template);
        }
        if (!claims.isEmpty()) {
            body.put("claims", claims);
        }
        if (pid) {
            body.put("pid", true);
        }
        if (!omit.isEmpty()) {
            body.put("omit", omit);
        }
        if (!alwaysDisclosed.isEmpty()) {
            body.put("always_disclosed", alwaysDisclosed);
        }
        if (saveAsTemplate != null) {
            body.put("save_as_template", saveAsTemplate);
        }
        if (vct != null) {
            body.put("vct", vct);
        }
        if (docType != null) {
            body.put("doctype", docType);
        }
        if (namespace != null) {
            body.put("namespace", namespace);
        }
        if (exp != null) {
            body.put("exp", exp);
        }
        if (nbf != null) {
            body.put("nbf", nbf);
        }
        if (statusListUri != null) {
            body.put("status_list_uri", statusListUri);
        }
        if (statusListIdx != null) {
            body.put("status_list_idx", statusListIdx);
        }
        if (trustProfile != null) {
            body.put("trust_profile", trustProfile);
        }
        return body;
    }
}
