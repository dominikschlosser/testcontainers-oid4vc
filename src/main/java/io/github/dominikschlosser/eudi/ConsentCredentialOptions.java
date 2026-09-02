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
 * Every way the wallet could answer a presentation request. The first
 * satisfiable option of every set and the first candidate of every query are
 * the wallet's own choice, so an approval that changes nothing presents what
 * auto-accept presents. Reference these options from a
 * {@link ConsentApproval} to present a different credential.
 *
 * @param sets    one entry per DCQL {@code credential_sets} entry the wallet
 *                can satisfy, empty for a request without credential sets,
 *                where every query is required
 * @param queries the matching credentials per DCQL credential query id
 */
public record ConsentCredentialOptions(List<ConsentSetOptions> sets, List<ConsentQueryOptions> queries) {

    /**
     * Returns the options for one DCQL credential query id, or {@code null}
     * if the request has no such query.
     */
    public ConsentQueryOptions query(String id) {
        return queries.stream()
                .filter(query -> query.id().equals(id))
                .findFirst()
                .orElse(null);
    }
}
