package com.allanvital.devzao.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record LanguageChartView(List<LanguageShareView> shares, String gradient) {

    public static LanguageChartView empty() {
        return new LanguageChartView(List.of(), "#ebe5dc");
    }

    public static LanguageChartView fromCounts(List<LanguageCountInput> counts) {
        long total = counts.stream().mapToLong(LanguageCountInput::repositoryCount).sum();
        if (total == 0) {
            return empty();
        }

        List<LanguageShareView> shares = new ArrayList<>();
        List<String> gradientParts = new ArrayList<>();
        double current = 0.0d;

        for (LanguageCountInput count : counts) {
            String color = LanguageColorPalette.colorFor(count.language());
            double next = current + ((double) count.repositoryCount() / (double) total * 100.0d);
            int percentage = Math.max(1, (int) Math.round((double) count.repositoryCount() / (double) total * 100.0d));
            shares.add(new LanguageShareView(count.language(), count.repositoryCount(), percentage, color));
            gradientParts.add(color + " " + formatPercent(current) + "% " + formatPercent(next) + "%");
            current = next;
        }

        return new LanguageChartView(shares, "conic-gradient(" + String.join(", ", gradientParts) + ")");
    }

    private static String formatPercent(double percentage) {
        return String.format(Locale.ROOT, "%.2f", percentage);
    }

    public record LanguageCountInput(String language, long repositoryCount) {
    }
}
