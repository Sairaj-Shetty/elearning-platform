package com.lumina.common;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugger {
    private Slugger() {}

    public static String slug(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "item" : normalized;
    }
}
