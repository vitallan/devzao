package com.allanvital.devzao.web;

import java.time.Duration;
import java.time.Instant;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record TopRepositoryView(
        String name,
        String htmlUrl,
        int stargazersCount,
        String language,
        String languageColor,
        String pushedAtRelative,
        String pushedAtCategoryColor
) {

    public static TopRepositoryView create(String name, String htmlUrl, int stargazersCount, String language, Instant pushedAt) {
        String pushedAtRelative = relativeTime(pushedAt);
        String pushedAtCategoryColor = categoryColor(category(pushedAt));
        return new TopRepositoryView(name, htmlUrl, stargazersCount, language, LanguageColorPalette.colorFor(language), pushedAtRelative, pushedAtCategoryColor);
    }

    static String relativeTime(Instant pushedAt) {
        if (pushedAt == null) {
            return "never pushed";
        }

        long seconds = Duration.between(pushedAt, Instant.now()).abs().getSeconds();
        if (seconds < 60) {
            return "moments ago";
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }

        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }

        long days = hours / 24;
        if (days < 30) {
            return days + "d ago";
        }

        long months = days / 30;
        if (months < 12) {
            return months + "mo ago";
        }

        long years = days / 365;
        return years + "y ago";
    }

    static String category(Instant pushedAt) {
        if (pushedAt == null) {
            return "unknown";
        }

        long days = Duration.between(pushedAt, Instant.now()).abs().toDays();
        if (days < 30) {
            return "veryRecent";
        }
        if (days < 180) {
            return "recent";
        }
        if (days < 365) {
            return "stale";
        }
        return "inactive";
    }

    static String categoryColor(String category) {
        return switch (category) {
            case "veryRecent" -> "#22c55e";
            case "recent" -> "#0ea5e9";
            case "stale" -> "#f59e0b";
            case "inactive" -> "#94a3b8";
            default -> "#d1d5db";
        };
    }

}
