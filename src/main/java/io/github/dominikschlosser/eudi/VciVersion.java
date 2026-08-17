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
 * The OpenID4VCI feature level the wallet uses as a client
 * ({@code --vci-version} since eudi-dev v1.23.0).
 *
 * <p>{@link #V1_0} is the published OpenID4VCI 1.0 and the default.
 * {@link #V1_1} additionally uses what the 1.1 draft adds where an issuer
 * offers it — most notably interactive authorization (OpenID4VCI 1.1 §6):
 * against an authorization server that publishes an
 * {@code authorization_challenge_endpoint}, the wallet redeems an
 * authorization-code offer by presenting a credential it holds instead of
 * going through a browser redirect. Every 1.1 feature is negotiated in the
 * issuer's metadata, so against a plain 1.0 issuer the two levels behave
 * identically.
 */
public enum VciVersion {
    V1_0("1.0"),
    V1_1("1.1");

    private final String wireValue;

    VciVersion(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }
}
