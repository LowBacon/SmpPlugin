package com.bx.smpPlugin.coinflip.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs CoinFlip storage work on one dedicated thread with its own connection, so the server thread never waits
 * for the database. Work is queued in a bounded queue: when it is full, new work fails immediately with
 * {@code BUSY} instead of piling up. Each {@link #transaction} commits or rolls back as a whole.
 */
public final class CoinFlipRepository {

    /** Work that runs inside one database transaction. */
    @FunctionalInterface
    public interface Work<T> {
        T run(CoinFlipDao dao) throws SQLException;
    }

    /** Opens a connection; the plugin passes the shared database's dedicated-connection factory. */
    @FunctionalInterface
    public interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    private final ConnectionFactory factory;
    private final boolean mySql;
    private final int queryTimeoutSeconds;
    private final Logger logger;
    private final ThreadPoolExecutor worker;
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong slow = new AtomicLong();
    private final AtomicLong totalNanos = new AtomicLong();
    private volatile Connection connection;
    private volatile boolean ready;
    private volatile boolean closed;

    public CoinFlipRepository(ConnectionFactory factory, boolean mySql, int queryTimeoutSeconds, int maxQueued, Logger logger) {
        this.factory = factory;
        this.mySql = mySql;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.logger = logger;
        this.worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(maxQueued), runnable -> {
            Thread thread = new Thread(runnable, "SmpPlugin-CoinFlipDB");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Opens the connection, creates or migrates the schema, and marks storage ready. */
    public CompletableFuture<Void> initialize() {
        return submit(true, dao -> {
            // CREATE TABLE IF NOT EXISTS never alters existing tables, so it is safe before the version check.
            dao.createSchema();
            int version = dao.schemaVersion();
            if (version > CoinFlipDao.SCHEMA_VERSION) {
                throw new SQLException("CoinFlip tables are from a newer plugin version (schema " + version
                        + "); refusing to use them");
            }
            // Future migrations run here, one version at a time: if (version < 2) { ...; } etc.
            if (version < CoinFlipDao.SCHEMA_VERSION) {
                dao.setSchemaVersion(CoinFlipDao.SCHEMA_VERSION);
            }
            return null;
        }).thenRun(() -> ready = true);
    }

    public boolean isReady() {
        return ready && !closed;
    }

    /** Runs {@code work} in one transaction. Failure is reported as a {@link CoinFlipStorageException}. */
    public <T> CompletableFuture<T> transaction(Work<T> work) {
        return submit(false, work);
    }

    /** Pending work in the queue. */
    public int queued() {
        return worker.getQueue().size();
    }

    public long completedCount() {
        return completed.get();
    }

    public long failedCount() {
        return failed.get();
    }

    public long slowCount() {
        return slow.get();
    }

    public double averageMillis() {
        long count = completed.get() + failed.get();
        return count == 0 ? 0D : totalNanos.get() / 1_000_000D / count;
    }

    /** Stops accepting work, lets queued work finish (up to {@code waitSeconds}), then closes the connection. */
    public void shutdown(int waitSeconds) {
        closed = true;
        worker.shutdown();
        try {
            if (!worker.awaitTermination(waitSeconds, TimeUnit.SECONDS)) {
                logger.warning("[CoinFlip] Storage work still running at shutdown; unfinished work is recovered at next start.");
                worker.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
        Connection current = connection;
        if (current != null) {
            try {
                current.close();
            } catch (SQLException ignored) {
                // closing anyway
            }
        }
    }

    private <T> CompletableFuture<T> submit(boolean initializing, Work<T> work) {
        if (closed) {
            return CompletableFuture.failedFuture(new CoinFlipStorageException(
                    CoinFlipStorageException.Reason.UNAVAILABLE, "storage is shut down"));
        }
        if (!initializing && !ready) {
            return CompletableFuture.failedFuture(new CoinFlipStorageException(
                    CoinFlipStorageException.Reason.UNAVAILABLE, "storage is not ready"));
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            worker.execute(() -> run(work, future));
        } catch (RejectedExecutionException rejected) {
            future.completeExceptionally(new CoinFlipStorageException(
                    closed ? CoinFlipStorageException.Reason.UNAVAILABLE : CoinFlipStorageException.Reason.BUSY,
                    "storage queue is full", rejected));
        }
        return future;
    }

    private <T> void run(Work<T> work, CompletableFuture<T> future) {
        long started = System.nanoTime();
        Connection current;
        try {
            current = connection();
        } catch (SQLException exception) {
            failed.incrementAndGet();
            future.completeExceptionally(new CoinFlipStorageException(CoinFlipStorageException.Reason.UNAVAILABLE,
                    "cannot connect: " + exception.getMessage(), exception));
            return;
        }
        T result;
        try {
            current.setAutoCommit(false);
            result = work.run(new CoinFlipDao(current, mySql, queryTimeoutSeconds));
        } catch (CoinFlipStorageException exception) {
            rollback(current);
            failed.incrementAndGet();
            future.completeExceptionally(exception);
            record(started);
            return;
        } catch (SQLException | RuntimeException exception) {
            rollback(current);
            failed.incrementAndGet();
            logger.log(Level.WARNING, "[CoinFlip] Storage work failed and was rolled back", exception);
            future.completeExceptionally(new CoinFlipStorageException(CoinFlipStorageException.Reason.FAILED,
                    String.valueOf(exception.getMessage()), exception));
            record(started);
            return;
        }
        try {
            current.commit();
        } catch (SQLException exception) {
            failed.incrementAndGet();
            logger.log(Level.SEVERE, "[CoinFlip] Commit failed; the outcome is unknown and will be reconciled", exception);
            rollback(current);
            future.completeExceptionally(new CoinFlipStorageException(CoinFlipStorageException.Reason.UNCERTAIN,
                    "commit failed: " + exception.getMessage(), exception));
            record(started);
            return;
        }
        completed.incrementAndGet();
        record(started);
        future.complete(result);
    }

    private void record(long startedNanos) {
        long elapsed = System.nanoTime() - startedNanos;
        totalNanos.addAndGet(elapsed);
        if (elapsed > TimeUnit.MILLISECONDS.toNanos(250)) {
            slow.incrementAndGet();
        }
    }

    private Connection connection() throws SQLException {
        Connection current = connection;
        if (current != null) {
            try {
                if (!current.isClosed() && current.isValid(2)) {
                    return current;
                }
            } catch (SQLException ignored) {
                // reopen below
            }
            try {
                current.close();
            } catch (SQLException ignored) {
                // already broken
            }
        }
        connection = factory.open();
        return connection;
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // the connection is reopened on the next use if it is broken
        }
    }
}
