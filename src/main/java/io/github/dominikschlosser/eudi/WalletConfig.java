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
 * The wallet instance's introspection document ({@code GET /api/config}):
 * identity, storage locations, advertised URLs, and runtime behavior.
 *
 * <p>{@code tlsListener} is false when an external TLS terminator serves the
 * issuer origin (an https {@code --base-url}). The built-in HTTPS listener is
 * disabled then, so {@link EudiWalletContainer#getHttpsBaseUrl()} and the
 * exported wallet TLS certificate do not apply.
 *
 * <p>{@code vciVersion} is the OpenID4VCI feature level ({@code "1.0"} or
 * {@code "1.1"}, see {@link VciVersion}). {@code validationMode},
 * {@code vciVersion}, {@code requireHaip} and {@code requireEncryptedRequest}
 * are changeable at runtime through the {@link WalletClient} conformance
 * setters.
 */
public record WalletConfig(
        int pid,
        int port,
        String buildId,
        String version,
        String walletDir,
        String templatesDir,
        String baseUrl,
        String issuerUrl,
        String statusListUrl,
        String preferredFormat,
        String validationMode,
        String vciVersion,
        boolean autoAccept,
        String sessionTranscript,
        boolean requireHaip,
        boolean requireHaipIssuance,
        boolean requireEncryptedRequest,
        boolean forceClientAttestation,
        int credentialCount,
        boolean tlsListener
) {
}
