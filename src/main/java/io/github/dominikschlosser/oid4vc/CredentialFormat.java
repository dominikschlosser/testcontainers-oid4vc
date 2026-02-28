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
package io.github.dominikschlosser.oid4vc;

public enum CredentialFormat {
    SD_JWT("dc+sd-jwt"),
    MSO_MDOC("mso_mdoc"),
    JWT_VC_JSON("jwt_vc_json");

    private final String wireValue;

    CredentialFormat(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }

    public static CredentialFormat fromWireValue(String value) {
        for (CredentialFormat format : values()) {
            if (format.wireValue.equals(value)) {
                return format;
            }
        }
        return null;
    }
}
