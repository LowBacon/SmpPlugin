package com.bx.smpPlugin.managers;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerWipeBlocksConcurrentRestoreTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/com/bx/smpPlugin/managers/PlayerWipeManager.java");

    @Test
    void wipeRefusesWhileRestoreIsRunning() throws Exception {
        String source = Files.readString(SOURCE);
        int start = source.indexOf("public WipeResult wipe(");
        int end = source.indexOf("private File writeBackup(");
        assertTrue(start >= 0 && end > start, "wipe should still sit above writeBackup");

        String method = source.substring(start, end);
        assertTrue(
                method.contains("isRestoreInProgress")
                        || method.contains("getPlayerUnwipeManager"),
                "PlayerUnwipeManager.restore refuses to run while a wipe is in progress. "
                        + "wipe() never asks whether a restore is running, so the two can rewrite "
                        + "the same player's rows at once."
        );
    }
}
