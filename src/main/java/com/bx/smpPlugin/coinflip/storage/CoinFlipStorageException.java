package com.bx.smpPlugin.coinflip.storage;

/**
 * A storage operation failed. {@link #definite()} says whether the outcome is known: true means the work was
 * rolled back (nothing was stored); false means a commit was attempted and may or may not have happened,
 * which callers must treat as an unknown money outcome.
 */
public class CoinFlipStorageException extends RuntimeException {

    private final boolean definite;
    private final Reason reason;

    public enum Reason {
        /** The database refused or failed the work and rolled it back. */
        FAILED,
        /** A compare-and-set found the row in a different state; nothing was changed. */
        CONFLICT,
        /** The bounded storage queue is full; the work was never started. */
        BUSY,
        /** Storage has not started or has shut down. */
        UNAVAILABLE,
        /** A business rule checked inside the transaction (e.g. a daily cap) refused the work. */
        REFUSED,
        /** The commit itself failed; the outcome is unknown. */
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

    /** Unwraps CompletionException/ExecutionException layers. */
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
