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
 * Status-list metadata of a credential.
 *
 * @param managed whether the referenced status list is managed by this wallet
 * @param status  the status value (0 = active, 1 = revoked); {@code null} in
 *                credential summaries of unmanaged entries, resolved live via
 *                {@link WalletClient#getCredentialStatus(String)}
 * @param source  where the status was resolved from ({@code wallet} or
 *                {@code remote}); only set by
 *                {@link WalletClient#getCredentialStatus(String)}
 * @param uri     the status list URI referenced by the credential
 * @param idx     the credential's index on the status list
 */
public record CredentialStatusInfo(boolean managed, Integer status, String source, String uri, Integer idx) {

    public boolean isRevoked() {
        return status != null && status != 0;
    }
}
