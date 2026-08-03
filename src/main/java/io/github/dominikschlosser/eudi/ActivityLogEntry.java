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
 * An entry of the wallet's activity log, useful for asserting what the wallet
 * actually did during a flow.
 *
 * @param time    RFC 3339 timestamp
 * @param action  short action name
 * @param detail  human-readable detail line
 * @param success whether the step succeeded
 * @param details structured per-step details, if any
 */
public record ActivityLogEntry(String time, String action, String detail, boolean success, Map<String, Object> details) {
}
