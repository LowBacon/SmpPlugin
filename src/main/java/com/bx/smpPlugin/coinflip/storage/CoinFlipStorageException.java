// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip.storage;

public class CoinFlipStorageException extends RuntimeException {

    private final boolean definite;
    private final Reason reason;

    public enum Reason {
        FAILED,
        CONFLICT,
        BUSY,
        UNAVAILABLE,
        REFUSED,
        UNCERTAIN
    }

    public CoinFlipStorageException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.definite = reason != Reason.UNCERTAIN;
    }

    public CoinFlipStorageException(Reason reason, String message) {
        this(reason, message, null);
    }

    public boolean definite() {
        return definite;
    }

    public Reason reason() {
        return reason;
    }

    public static CoinFlipStorageException from(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CoinFlipStorageException storage) {
                return storage;
            }
            current = current.getCause();
        }
        return new CoinFlipStorageException(Reason.FAILED, throwable == null ? "unknown" : String.valueOf(throwable.getMessage()), throwable);
    }
}
