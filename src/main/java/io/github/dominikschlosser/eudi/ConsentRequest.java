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

import java.util.List;

/**
 * A pending consent request of a wallet running without auto-accept, awaiting
 * {@link WalletClient#approveRequest(String)},
 * {@link WalletClient#approveRequest(String, ConsentApproval)} or
 * {@link WalletClient#denyRequest(String)}.
 *
 * @param id                 request id
 * @param type               request type, {@code presentation},
 *                           {@code issuance}, or
 *                           {@code issuance_presentation} for a presentation
 *                           an issuer asked for during an issuance
 * @param status             request status, {@code pending} until resolved
 * @param clientId           the requesting verifier's client id, if any
 * @param createdAt          RFC 3339 creation timestamp
 * @param matchedCredentials the credentials the wallet would present, its own
 *                           first choice per credential query
 * @param purposes           the purposes the verifier registered for this
 *                           data request, if any
 * @param credentialOptions  every credential the wallet could present
 *                           instead, {@code null} for an issuance request or
 *                           when there is nothing to choose
 */
public record ConsentRequest(
        String id,
        String type,
        String status,
        String clientId,
        String createdAt,
        List<CredentialMatch> matchedCredentials,
        List<String> purposes,
        ConsentCredentialOptions credentialOptions) {

    /**
     * Creates a consent request without the selection details, matching the
     * constructor of releases before 2.4.0. Matched credentials and purposes
     * are empty and credential options are absent.
     */
    public ConsentRequest(String id, String type, String status, String clientId, String createdAt) {
        this(id, type, status, clientId, createdAt, List.of(), List.of(), null);
    }
}
