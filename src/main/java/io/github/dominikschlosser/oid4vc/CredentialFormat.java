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
