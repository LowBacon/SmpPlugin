package com.bx.smpPlugin.managers;

import java.util.List;

/**
 * Page window for the Bedrock Quick Buy catalogue. Kept separate so the size cap can be tested
 * without a server: a form that lists every material is what freezes the Bedrock client.
 */
public final class QuickBuyBedrockPages {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 40;

    private QuickBuyBedrockPages() {
    }

    public static int pageSize(int configured) {
        if (configured <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(MAX_PAGE_SIZE, configured);
    }

    public static int pageCount(int items, int pageSize) {
        int size = Math.max(1, pageSize);
        if (items <= 0) {
            return 1;
        }
        return (items + size - 1) / size;
    }

    public static int clampPage(int page, int pages) {
        if (pages < 1) {
            return 0;
        }
        return Math.max(0, Math.min(page, pages - 1));
    }

    public static <T> List<T> slice(List<T> items, int page, int pageSize) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        int size = Math.max(1, pageSize);
        int pages = pageCount(items.size(), size);
        int safePage = clampPage(page, pages);
        int from = safePage * size;
        int to = Math.min(from + size, items.size());
        return List.copyOf(items.subList(from, to));
    }
}
