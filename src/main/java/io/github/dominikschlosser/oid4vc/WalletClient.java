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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    public String getTrustList() {
        return get(baseUrl + "/api/trustlist");
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
            if (redirectUri == null) {
                Object response = parsed.get("response");
                if (response instanceof Map<?, ?> responseMap) {
                    redirectUri = (String) responseMap.get("redirect_uri");
                }
            }
            return new PresentationResponse(redirectUri, body);
        } catch (IOException e) {
            return new PresentationResponse(null, body);
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
        return new Credential(id, format, type, claims);
    }
}
