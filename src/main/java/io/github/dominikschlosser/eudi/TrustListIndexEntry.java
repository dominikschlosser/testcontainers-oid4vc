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

import java.net.URI;
import java.util.List;

public record TrustListIndexEntry(
        String id,
        boolean defaultProfile,
        String path,
        String loteType,
        String entityName,
        String issuanceServiceType,
        String revocationServiceType,
        List<TrustListAttestation> attestations,
        String advertisedUrl,
        String url
) {

    /**
     * Resolves this trust-list discovery entry against the URL the caller
     * actually used to reach the wallet, such as the Testcontainers-mapped
     * {@code getBaseUrl()} or {@code getTrustListsUrl()}.
     */
    public String resolveUrl(String baseUrl) {
        if (path == null || path.isBlank()) {
            return advertisedUrl != null ? advertisedUrl : url;
        }
        return URI.create(baseUrl).resolve(path).toString();
    }
}
