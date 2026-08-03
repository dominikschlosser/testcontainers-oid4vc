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

import java.util.Map;

/**
 * A credential stored in the wallet.
 *
 * @param id     wallet-internal credential id
 * @param format credential format
 * @param type   the VCT (SD-JWT / JWT VC) or doc type (mDoc)
 * @param claims the credential's claims
 * @param raw    the raw credential (e.g. the SD-JWT compact serialization)
 * @param status status-list metadata, or {@code null} when the credential
 *               carries no status list reference
 */
public record Credential(
        String id,
        CredentialFormat format,
        String type,
        Map<String, Object> claims,
        String raw,
        CredentialStatusInfo status
) {
}
