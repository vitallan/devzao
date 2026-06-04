package com.allanvital.devzao.web;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record HourlyActivityView(
        int hour,
        String label,
        int commitCount,
        String displayCommitCount,
        double percentage
) {
    public static HourlyActivityView from(int hour, int commitCount, int maxCount) {
        double pct = maxCount > 0 ? (double) commitCount / maxCount * 100.0 : 0.0;
        String label = String.format("%02d:00", hour);
        return new HourlyActivityView(hour, label, commitCount, abbreviate(commitCount), pct);
    }

    private static String abbreviate(int count) {
        if (count >= 10_000) {
            return (count / 1_000) + "k";
        } else if (count >= 1_000) {
            return String.format("%.1fk", count / 1000.0);
        }
        return String.valueOf(count);
    }
}
