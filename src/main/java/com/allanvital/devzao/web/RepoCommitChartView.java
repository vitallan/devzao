package com.allanvital.devzao.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record RepoCommitChartView(List<RepoCommitShare> shares, String gradient) {

    public static RepoCommitChartView empty() {
        return new RepoCommitChartView(List.of(), "#ebe5dc");
    }

    public static RepoCommitChartView fromRepoCounts(List<RepoCommitInput> inputs) {
        long total = inputs.stream().mapToLong(RepoCommitInput::commitCount).sum();
        if (total == 0) {
            return empty();
        }

        List<RepoCommitShare> shares = new ArrayList<>();
        List<String> gradientParts = new ArrayList<>();
        double current = 0.0d;
        int index = 0;

        for (RepoCommitInput input : inputs) {
            String color = LanguageColorPalette.colorForRepoIndex(index++);
            double next = current + ((double) input.commitCount() / (double) total * 100.0d);
            shares.add(new RepoCommitShare(input.repoName(), input.commitCount(), color));
            gradientParts.add(color + " " + formatPercent(current) + "% " + formatPercent(next) + "%");
            current = next;
        }

        return new RepoCommitChartView(shares, "conic-gradient(" + String.join(", ", gradientParts) + ")");
    }

    private static String formatPercent(double percentage) {
        return String.format(Locale.ROOT, "%.2f", percentage);
    }

    public record RepoCommitInput(String repoName, int commitCount) {
    }

    public record RepoCommitShare(String repoName, int commitCount, String color) {
    }
}
