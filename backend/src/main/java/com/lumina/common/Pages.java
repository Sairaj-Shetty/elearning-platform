package com.lumina.common;

import java.util.List;

public final class Pages {
    private Pages() {}

    public static int page(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    public static int size(Integer size, int fallback) {
        if (size == null || size < 1) return fallback;
        return Math.min(size, 50);
    }

    public record PageData<T>(List<T> items, int page, int size, long total, int totalPages) {
        public static <T> PageData<T> of(List<T> items, int page, int size, long total) {
            int pages = size == 0 ? 0 : (int) Math.ceil(total / (double) size);
            return new PageData<>(items, page, size, total, pages);
        }
    }
}
