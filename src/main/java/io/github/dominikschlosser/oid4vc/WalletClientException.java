package io.github.dominikschlosser.oid4vc;

public class WalletClientException extends RuntimeException {

    public WalletClientException(String message) {
        super(message);
    }

    public WalletClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
