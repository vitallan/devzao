package com.allanvital.devzao.web;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
* @author Allan Vital (https://allanvital.com)
  */
public class LanguageColorPalette {

    private static final Map<String, String> COLORS_BY_LANGUAGE = Map.ofEntries(
            Map.entry("python", "#f7c948"),
            Map.entry("java", "#d97706"),
            Map.entry("javascript", "#f0db4f"),
            Map.entry("typescript", "#3178c6"),
            Map.entry("kotlin", "#7f52ff"),
            Map.entry("go", "#00add8"),
            Map.entry("c#", "#68217a"),
            Map.entry("rust", "#b7410e"),
            Map.entry("ruby", "#cc342d"),
            Map.entry("php", "#777bb4"),
            Map.entry("c", "#555555"),
            Map.entry("c++", "#00599c"),
            Map.entry("swift", "#f05138"),
            Map.entry("scala", "#dc322f"),
            Map.entry("shell", "#89e051"),
            Map.entry("html", "#e34c26"),
            Map.entry("css", "#563d7c")
    );

    private static final List<String> FALLBACK_COLORS = List.of(
            "#315c72",
            "#7c3aed",
            "#0f766e",
            "#be123c",
            "#4338ca",
            "#a16207",
            "#047857",
            "#b45309"
    );

    private static final List<String> REPO_CHART_COLORS = List.of(
            "#f7c948",
            "#60a5fa",
            "#fdba74",
            "#315c72",
            "#7c3aed",
            "#0f766e",
            "#be123c",
            "#4338ca"
    );

    private LanguageColorPalette() {
    }

    public static String colorForIndex(int index) {
        return FALLBACK_COLORS.get(index % FALLBACK_COLORS.size());
    }

    public static String colorForRepoIndex(int index) {
        return REPO_CHART_COLORS.get(index % REPO_CHART_COLORS.size());
    }

    public static String colorFor(String language) {
        String normalized = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        String knownColor = COLORS_BY_LANGUAGE.get(normalized);
        if (knownColor != null) {
            return knownColor;
        }

        int index = Math.floorMod(normalized.hashCode(), FALLBACK_COLORS.size());
        return FALLBACK_COLORS.get(index);
    }
}
