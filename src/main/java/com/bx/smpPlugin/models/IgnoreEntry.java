package com.bx.smpPlugin.models;

import java.util.UUID;

public record IgnoreEntry(
        UUID ownerUuid,
        UUID ignoredUuid,
        String ignoredNameSnapshot,
        long createdAt
) {
}
