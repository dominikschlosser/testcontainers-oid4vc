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
 * A named, reusable credential claim set with per-format defaults. Pre-defined
 * templates (e.g. {@code german-pid-sdjwt}, {@code german-pid-mdoc}) are
 * compiled into eudi-dev; user templates saved under a pre-defined template's
 * name override it.
 *
 * @param name            template name, unique per wallet
 * @param description     human-readable description
 * @param format          {@code sdjwt}, {@code jwt}, or {@code mdoc} (wire
 *                        values like {@code dc+sd-jwt} are accepted too)
 * @param vct             default VCT for SD-JWT / JWT VC credentials
 * @param docType         default doc type for mDoc credentials
 * @param namespace       default mDoc namespace
 * @param exp             default expiry as a Go duration string (e.g. {@code 24h})
 * @param claims          the claim set
 * @param alwaysDisclosed SD-JWT claims embedded plainly instead of selectively disclosable
 * @param predefined      whether the template is compiled into eudi-dev
 */
public record CredentialTemplate(
        String name,
        String description,
        String format,
        String vct,
        String docType,
        String namespace,
        String exp,
        Map<String, Object> claims,
        List<String> alwaysDisclosed,
        boolean predefined
) {

    public static CredentialTemplate named(String name) {
        return new CredentialTemplate(name, null, null, null, null, null, null, Map.of(), List.of(), false);
    }

    public CredentialTemplate withDescription(String description) {
        return new CredentialTemplate(name, description, format, vct, docType, namespace, exp, claims, alwaysDisclosed, predefined);
    }

    public CredentialTemplate withFormat(String format) {
        return new CredentialTemplate(name, description, format, vct, docType, namespace, exp, claims, alwaysDisclosed, predefined);
    }

    public CredentialTemplate withVct(String vct) {
        return new CredentialTemplate(name, description, format, vct, docType, namespace, exp, claims, alwaysDisclosed, predefined);
    }

    public CredentialTemplate withDocType(String docType) {
        return new CredentialTemplate(name, description, format, vct, docType, namespace, exp, claims, alwaysDisclosed, predefined);
    }

    public CredentialTemplate withNamespace(String namespace) {
        return new CredentialTemplate(name, description, format, vct, docType, namespace, exp, claims, alwaysDisclosed, predefined);
    }

    public CredentialTemplate withExp(String exp) {
        return new CredentialTemplate(name, description, format, vct, docType, namespace, exp, claims, alwaysDisclosed, predefined);
    }

    public CredentialTemplate withClaims(Map<String, Object> claims) {
        return new CredentialTemplate(name, description, format, vct, docType, namespace, exp, claims, alwaysDisclosed, predefined);
    }

    public CredentialTemplate withAlwaysDisclosed(List<String> alwaysDisclosed) {
        return new CredentialTemplate(name, description, format, vct, docType, namespace, exp, claims, alwaysDisclosed, predefined);
    }
}
