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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WalletClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient httpClient;

    WalletClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    public List<Credential> getCredentials() {
        String body = get(baseUrl + "/api/credentials");
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(body, new TypeReference<>() {});
            return raw.stream()
                    .map(WalletClient::toCredential)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new WalletClientException("Failed to parse credentials response", e);
        }
    }

    public Credential getCredential(String id) {
        String body = get(baseUrl + "/api/credentials/" + urlEncode(id));
        return toCredential(parseObject(body, "credential response"));
    }

    /**
     * Deletes all stored credentials and returns the number of deleted entries.
     */
    public int deleteAllCredentials() {
        String body = delete(baseUrl + "/api/credentials");
        Object deleted = parseObject(body, "delete-all response").get("deleted");
        return deleted instanceof Number number ? number.intValue() : 0;
    }

    /**
     * Resolves the live status of a credential: from the wallet's own status
     * list when managed here, otherwise by fetching the external status list
     * referenced by the credential.
     */
    public CredentialStatusInfo getCredentialStatus(String id) {
        String body = get(baseUrl + "/api/credentials/" + urlEncode(id) + "/status");
        return toStatusInfo(parseObject(body, "credential status response"));
    }

    /**
     * Issues a credential with the wallet's issuer key and imports it into the
     * wallet, mirroring {@code eudi issue sdjwt|jwt|mdoc --wallet}.
     */
    public Credential issueCredential(IssueRequest request) {
        String body = postJson(baseUrl + "/api/issue", toJson(request.toBody()));
        return toCredential(parseObject(body, "issue response"));
    }

    /**
     * Returns all credential templates (pre-defined and user), including their
     * claims.
     */
    public List<CredentialTemplate> getTemplates() {
        String body = get(baseUrl + "/api/templates");
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(body, new TypeReference<>() {});
            return raw.stream()
                    .map(WalletClient::toTemplate)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new WalletClientException("Failed to parse templates response", e);
        }
    }

    public CredentialTemplate getTemplate(String name) {
        String body = get(baseUrl + "/api/templates/" + urlEncode(name));
        return toTemplate(parseObject(body, "template response"));
    }

    /**
     * Creates or replaces a user template. Saving under a pre-defined
     * template's name overrides it.
     */
    public CredentialTemplate saveTemplate(CredentialTemplate template) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        if (template.description() != null) {
            body.put("description", template.description());
        }
        if (template.format() != null) {
            body.put("format", template.format());
        }
        if (template.vct() != null) {
            body.put("vct", template.vct());
        }
        if (template.docType() != null) {
            body.put("doctype", template.docType());
        }
        if (template.namespace() != null) {
            body.put("namespace", template.namespace());
        }
        if (template.exp() != null) {
            body.put("exp", template.exp());
        }
        body.put("claims", template.claims());
        if (template.alwaysDisclosed() != null && !template.alwaysDisclosed().isEmpty()) {
            body.put("always_disclosed", template.alwaysDisclosed());
        }
        String response = putJson(baseUrl + "/api/templates/" + urlEncode(template.name()), toJson(body));
        return toTemplate(parseObject(response, "template response"));
    }

    /**
     * Removes a user template. Pre-defined templates cannot be deleted;
     * deleting a user template that overrides one restores the pre-defined
     * version.
     */
    public void deleteTemplate(String name) {
        delete(baseUrl + "/api/templates/" + urlEncode(name));
    }

    /**
     * Returns the shared wallet CA certificate as PEM.
     */
    public String getCaCertificatePem() {
        return get(baseUrl + "/api/certificates/ca");
    }

    /**
     * Returns the shared wallet CA certificate as a JWKS document (public key
     * with {@code x5c} chain).
     */
    public String getCaCertificateJwks() {
        return get(baseUrl + "/api/certificates/ca?format=jwks");
    }

    /**
     * Returns the wallet's HTTPS leaf certificate as PEM.
     */
    public String getTlsCertificatePem() {
        return get(baseUrl + "/api/certificates/tls");
    }

    /**
     * Returns the wallet's HTTPS leaf certificate as a JWKS document (public
     * key with {@code x5c} chain).
     */
    public String getTlsCertificateJwks() {
        return get(baseUrl + "/api/certificates/tls?format=jwks");
    }

    /**
     * Returns the wallet's activity log, useful for asserting what the wallet
     * actually did during a flow.
     */
    public List<ActivityLogEntry> getActivityLog() {
        String body = get(baseUrl + "/api/log");
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(body, new TypeReference<>() {});
            return raw.stream()
                    .map(WalletClient::toActivityLogEntry)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new WalletClientException("Failed to parse activity log response", e);
        }
    }

    public void clearActivityLog() {
        delete(baseUrl + "/api/log");
    }

    /**
     * Returns the pending consent requests of a wallet running without
     * auto-accept.
     */
    public List<ConsentRequest> getPendingRequests() {
        String body = get(baseUrl + "/api/requests");
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(body, new TypeReference<>() {});
            return raw.stream()
                    .map(WalletClient::toConsentRequest)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new WalletClientException("Failed to parse pending requests response", e);
        }
    }

    /**
     * Approves a pending consent request, disclosing all matched claims, and
     * waits for the submission result.
     */
    public ApprovalResult approveRequest(String id) {
        return approveRequest(id, null);
    }

    /**
     * Approves a pending consent request, disclosing only the selected claims
     * per credential id, and waits for the submission result.
     */
    public ApprovalResult approveRequest(String id, Map<String, List<String>> selectedClaims) {
        String body = selectedClaims == null
                ? toJson(Map.of())
                : toJson(Map.of("selected_claims", selectedClaims));
        String response = postJson(baseUrl + "/api/requests/" + urlEncode(id) + "/approve", body);
        Map<String, Object> parsed = parseObject(response, "approval response");
        return new ApprovalResult(
                (String) parsed.get("status"),
                (String) parsed.get("redirect_uri"),
                (String) parsed.get("error")
        );
    }

    /**
     * Denies a pending consent request.
     */
    public void denyRequest(String id) {
        postJson(baseUrl + "/api/requests/" + urlEncode(id) + "/deny", toJson(Map.of()));
    }

    /**
     * Returns the wallet instance's introspection document.
     */
    public WalletConfig getConfig() {
        Map<String, Object> raw = parseObject(get(baseUrl + "/api/config"), "config response");
        return new WalletConfig(
                asInt(raw.get("pid")),
                asInt(raw.get("port")),
                (String) raw.get("build_id"),
                (String) raw.get("wallet_dir"),
                (String) raw.get("templates_dir"),
                (String) raw.get("base_url"),
                (String) raw.get("issuer_url"),
                (String) raw.get("status_list_url"),
                (String) raw.get("preferred_format"),
                (String) raw.get("validation_mode"),
                Boolean.TRUE.equals(raw.get("auto_accept")),
                (String) raw.get("session_transcript"),
                Boolean.TRUE.equals(raw.get("require_haip")),
                Boolean.TRUE.equals(raw.get("require_encrypted_request")),
                asInt(raw.get("credential_count"))
        );
    }

    public String getTrustList() {
        return get(baseUrl + "/api/trustlist");
    }

    public String getTrustListById(String id) {
        return get(baseUrl + "/api/trustlists/" + urlEncode(id));
    }

    public String getTrustListForVct(String vct) {
        return get(baseUrl + "/api/trustlist?vct=" + urlEncode(vct));
    }

    public String getTrustListForDocType(String docType) {
        return get(baseUrl + "/api/trustlist?doctype=" + urlEncode(docType));
    }

    public List<TrustListIndexEntry> getTrustLists() {
        String body = get(baseUrl + "/api/trustlists");
        try {
            Map<String, Object> raw = MAPPER.readValue(body, new TypeReference<>() {});
            Object trustLists = raw.get("trust_lists");
            if (!(trustLists instanceof List<?> entries)) {
                throw new WalletClientException("Failed to parse trust-list index response");
            }
            return entries.stream()
                    .map(WalletClient::toTrustListIndexEntry)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new WalletClientException("Failed to parse trust-list index response", e);
        }
    }

    public String getStatusList() {
        return get(baseUrl + "/api/statuslist");
    }

    public void setNextError(String error, String errorDescription) {
        postJson(baseUrl + "/api/next-error", toJson(Map.of("error", error, "error_description", errorDescription)));
    }

    public void clearNextError() {
        delete(baseUrl + "/api/next-error");
    }

    public void setPreferredFormat(CredentialFormat format) {
        putJson(baseUrl + "/api/config/preferred-format", toJson(Map.of("format", format.getWireValue())));
    }

    public void clearPreferredFormat() {
        putJson(baseUrl + "/api/config/preferred-format", toJson(Map.of("format", "")));
    }

    public void importCredential(String rawCredential) {
        postRaw(baseUrl + "/api/credentials", rawCredential);
    }

    public void setCredentialStatus(String credentialId, int status) {
        postJson(baseUrl + "/api/credentials/" + credentialId + "/status", toJson(Map.of("status", status)));
    }

    public void revokeCredential(String credentialId) {
        setCredentialStatus(credentialId, 1);
    }

    public void unrevokeCredential(String credentialId) {
        setCredentialStatus(credentialId, 0);
    }

    public PresentationResponse acceptPresentationRequest(String uri) {
        String body = postJson(baseUrl + "/api/presentations", toJson(Map.of("uri", uri)));
        try {
            Map<String, Object> parsed = MAPPER.readValue(body, new TypeReference<>() {});
            String redirectUri = (String) parsed.get("redirect_uri");
            String idToken = (String) parsed.get("id_token");
            if (redirectUri == null) {
                Object response = parsed.get("response");
                if (response instanceof Map<?, ?> responseMap) {
                    redirectUri = (String) responseMap.get("redirect_uri");
                    if (idToken == null) {
                        idToken = (String) responseMap.get("id_token");
                    }
                }
            }
            return new PresentationResponse(redirectUri, idToken, body);
        } catch (IOException e) {
            return new PresentationResponse(null, null, body);
        }
    }

    public OfferResponse acceptCredentialOffer(String uri) {
        String body = postJson(baseUrl + "/api/offers", toJson(Map.of("uri", uri)));
        return new OfferResponse(body);
    }

    public void deleteCredential(String id) {
        delete(baseUrl + "/api/credentials/" + id);
    }

    public boolean hasCredentialWithType(String type) {
        return getCredentials().stream()
                .anyMatch(c -> type.equals(c.type()));
    }

    public List<Credential> getCredentialsByType(String type) {
        return getCredentials().stream()
                .filter(c -> type.equals(c.type()))
                .collect(Collectors.toList());
    }

    public void deleteCredentialsByType(String type) {
        getCredentials().stream()
                .filter(c -> type.equals(c.type()))
                .forEach(c -> deleteCredential(c.id()));
    }

    private String get(String url) {
        return sendRequest(HttpRequest.newBuilder().uri(URI.create(url)).GET().build());
    }

    private String postJson(String url, String body) {
        return sendRequest(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private String postRaw(String url, String body) {
        return sendRequest(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private String putJson(String url, String body) {
        return sendRequest(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private String delete(String url) {
        return sendRequest(HttpRequest.newBuilder().uri(URI.create(url)).DELETE().build());
    }

    private String sendRequest(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new WalletClientException("HTTP " + response.statusCode() + " " + request.method()
                        + " " + request.uri() + ": " + response.body());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new WalletClientException("HTTP request failed: " + request.method() + " " + request.uri(), e);
        }
    }

    private static String toJson(Map<String, ?> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (IOException e) {
            throw new WalletClientException("Failed to serialize request body", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Credential toCredential(Map<String, Object> raw) {
        String id = (String) raw.get("id");
        CredentialFormat format = CredentialFormat.fromWireValue((String) raw.get("format"));
        String type = (String) raw.get("type");
        if (type == null) {
            type = (String) raw.get("vct");
        }
        if (type == null) {
            type = (String) raw.get("doctype");
        }
        Map<String, Object> claims = (Map<String, Object>) raw.getOrDefault("claims", Map.of());
        CredentialStatusInfo status = raw.get("status") instanceof Map<?, ?> statusMap
                ? toStatusInfo((Map<String, Object>) statusMap)
                : null;
        return new Credential(id, format, type, claims, (String) raw.get("raw"), status);
    }

    private static CredentialStatusInfo toStatusInfo(Map<String, Object> raw) {
        return new CredentialStatusInfo(
                Boolean.TRUE.equals(raw.get("managed")),
                raw.get("status") instanceof Number status ? status.intValue() : null,
                (String) raw.get("source"),
                (String) raw.get("uri"),
                raw.get("idx") instanceof Number idx ? idx.intValue() : null
        );
    }

    @SuppressWarnings("unchecked")
    private static CredentialTemplate toTemplate(Map<String, Object> raw) {
        return new CredentialTemplate(
                (String) raw.get("name"),
                (String) raw.get("description"),
                (String) raw.get("format"),
                (String) raw.get("vct"),
                (String) raw.get("doctype"),
                (String) raw.get("namespace"),
                (String) raw.get("exp"),
                (Map<String, Object>) raw.getOrDefault("claims", Map.of()),
                (List<String>) raw.getOrDefault("always_disclosed", List.of()),
                Boolean.TRUE.equals(raw.get("predefined"))
        );
    }

    @SuppressWarnings("unchecked")
    private static ActivityLogEntry toActivityLogEntry(Map<String, Object> raw) {
        return new ActivityLogEntry(
                (String) raw.get("time"),
                (String) raw.get("action"),
                (String) raw.get("detail"),
                Boolean.TRUE.equals(raw.get("success")),
                (Map<String, Object>) raw.getOrDefault("details", Map.of())
        );
    }

    private static ConsentRequest toConsentRequest(Map<String, Object> raw) {
        return new ConsentRequest(
                (String) raw.get("id"),
                (String) raw.get("type"),
                (String) raw.get("status"),
                (String) raw.get("client_id"),
                (String) raw.get("created_at")
        );
    }

    private static Map<String, Object> parseObject(String body, String description) {
        try {
            return MAPPER.readValue(body, new TypeReference<>() {});
        } catch (IOException e) {
            throw new WalletClientException("Failed to parse " + description, e);
        }
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private static TrustListIndexEntry toTrustListIndexEntry(Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap)) {
            throw new WalletClientException("Failed to parse trust-list index entry");
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        List<TrustListAttestation> attestations = ((List<?>) map.getOrDefault("attestations", List.of())).stream()
                .map(WalletClient::toTrustListAttestation)
                .collect(Collectors.toList());
        return new TrustListIndexEntry(
                (String) map.get("id"),
                Boolean.TRUE.equals(map.get("default")),
                (String) map.get("path"),
                (String) map.get("loTEType"),
                (String) map.get("entityName"),
                (String) map.get("issuanceServiceType"),
                (String) map.get("revocationServiceType"),
                attestations,
                (String) map.get("advertised_url"),
                (String) map.get("url")
        );
    }

    @SuppressWarnings("unchecked")
    private static TrustListAttestation toTrustListAttestation(Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap)) {
            throw new WalletClientException("Failed to parse trust-list attestation");
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        return new TrustListAttestation(
                (String) map.get("format"),
                (String) map.get("vct"),
                (String) map.get("doctype"),
                (List<String>) map.getOrDefault("entitlements", List.of()),
                (String) map.get("trust_list_type"),
                (String) map.get("status_determination_approach"),
                (String) map.get("scheme_type_community_rules"),
                (String) map.get("scheme_territory"),
                (String) map.get("entity_name"),
                (String) map.get("issuance_service_type"),
                (String) map.get("revocation_service_type"),
                (String) map.get("issuance_service_name"),
                (String) map.get("revocation_service_name")
        );
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
