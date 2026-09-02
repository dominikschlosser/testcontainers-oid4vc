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
 * One DCQL {@code credential_sets} entry as the consent request offers it.
 *
 * @param options  the satisfiable options in the order the wallet prefers
 *                 them, each a list of credential query ids that answer the
 *                 set together
 * @param optional whether the set may be skipped entirely
 *                 ({@code required: false})
 */
public record ConsentSetOptions(List<List<String>> options, boolean optional) {
}
