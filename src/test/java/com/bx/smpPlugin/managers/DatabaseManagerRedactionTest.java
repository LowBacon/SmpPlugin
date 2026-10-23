// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DatabaseManagerRedactionTest {

    @Test
    void credentialsInConnectionStringsNeverReachTheLog() {
        assertEquals("Timed out after 30000 ms: mongodb://***@db.example.com:27017/smp",
                DatabaseManager.redact("Timed out after 30000 ms: mongodb://admin:hunter2@db.example.com:27017/smp"));
        assertEquals("mongodb+srv://***@cluster0.example.net",
                DatabaseManager.redact("mongodb+srv://u:p%40ss@cluster0.example.net"));
        assertEquals("jdbc:mysql://host:3306/smp?user=root&password=***&useSSL=true",
                DatabaseManager.redact("jdbc:mysql://host:3306/smp?user=root&password=s3cret&useSSL=true"));
        assertEquals("mongodb://localhost:27017", DatabaseManager.redact("mongodb://localhost:27017"));
        assertEquals(null, DatabaseManager.redact(null));
    }

    @Test
    void theWholeCauseChainIsCleaned() {
        Exception cause = new IllegalArgumentException("bad uri mongodb://a:secret@h");
        Throwable cleaned = DatabaseManager.redacted(new RuntimeException("wrapper", cause), 0);
        assertFalse(cleaned.getCause().getMessage().contains("secret"), cleaned.getCause().getMessage());
        assertEquals(cause.getStackTrace().length, cleaned.getCause().getStackTrace().length);
    }
}
