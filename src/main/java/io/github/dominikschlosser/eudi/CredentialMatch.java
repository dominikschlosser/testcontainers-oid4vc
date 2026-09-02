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
import java.util.Map;

/**
 * A credential matched against one DCQL credential query of a presentation
 * request.
 *
 * @param queryId            the DCQL credential query id this match answers
 * @param credentialId       the matched credential's id
 * @param format             the credential format wire name, {@code dc+sd-jwt}
 *                           or {@code mso_mdoc}
 * @param vct                the SD-JWT credential type, if any
 * @param docType            the mdoc document type, if any
 * @param claims             the claim values the credential carries
 * @param selectedKeys       the claim selectors the wallet would disclose
 * @param untrustedAuthority whether the request's {@code trusted_authorities}
 *                           did not match this credential (offered anyway in
 *                           debug mode, refused in strict mode)
 * @param emptyArrayClaims   requested claim paths that select a selectively
 *                           disclosable array without selecting its elements,
 *                           disclosed as an empty array
 * @param missingClaims      requested claim paths this credential cannot
 *                           satisfy
 */
public record CredentialMatch(
        String queryId,
        String credentialId,
        String format,
        String vct,
        String docType,
        Map<String, Object> claims,
        List<String> selectedKeys,
        boolean untrustedAuthority,
        List<String> emptyArrayClaims,
        List<String> missingClaims) {
}
