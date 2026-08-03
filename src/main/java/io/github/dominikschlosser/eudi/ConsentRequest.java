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

/**
 * A pending consent request of a wallet running without auto-accept, awaiting
 * {@link WalletClient#approveRequest(String)} or
 * {@link WalletClient#denyRequest(String)}.
 *
 * @param id        request id
 * @param type      request type, {@code presentation} or {@code issuance}
 * @param status    request status, {@code pending} until resolved
 * @param clientId  the requesting verifier's client id, if any
 * @param createdAt RFC 3339 creation timestamp
 */
public record ConsentRequest(String id, String type, String status, String clientId, String createdAt) {
}
