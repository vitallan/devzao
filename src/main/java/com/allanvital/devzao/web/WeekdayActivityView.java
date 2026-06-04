package com.allanvital.devzao.web;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record WeekdayActivityView(
        String dayLabel,
        int commitCount,
        String displayCommitCount,
        double percentage
) {
    private static final String[] DAY_LABELS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    public static WeekdayActivityView from(int dayOfWeek, int commitCount, int maxCount) {
        double pct = maxCount > 0 ? (double) commitCount / maxCount * 100.0 : 0.0;
        return new WeekdayActivityView(DAY_LABELS[dayOfWeek], commitCount, abbreviate(commitCount), pct);
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
