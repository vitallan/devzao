package com.allanvital.devzao.web;

import com.allanvital.devzao.domain.GitMonthlyActivity;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record MonthlyActivityView(
        String yearMonth,
        String label,
        int commitCount,
        String displayCommitCount,
        double percentage
) {
    public static MonthlyActivityView from(GitMonthlyActivity activity, int maxCount) {
        return from(activity.getYearMonth(), activity.getCommitCount(), maxCount);
    }

    public static MonthlyActivityView from(String yearMonth, int commitCount, int maxCount) {
        double pct = maxCount > 0 ? (double) commitCount / maxCount * 100.0 : 0.0;
        return new MonthlyActivityView(yearMonth, formatMonthLabel(yearMonth), commitCount,
                abbreviate(commitCount), pct);
    }

    private static String abbreviate(int count) {
        if (count >= 10_000) {
            return (count / 1_000) + "k";
        } else if (count >= 1_000) {
            return String.format("%.1fk", count / 1000.0);
        }
        return String.valueOf(count);
    }

    private static String formatMonthLabel(String yearMonth) {
        String[] parts = yearMonth.split("-");
        if (parts.length != 2) {
            return yearMonth;
        }
        int month = Integer.parseInt(parts[1]);
        String yearShort = parts[0].substring(2);
        return switch (month) {
            case 1 -> "Jan";
            case 2 -> "Feb";
            case 3 -> "Mar";
            case 4 -> "Apr";
            case 5 -> "May";
            case 6 -> "Jun";
            case 7 -> "Jul";
            case 8 -> "Aug";
            case 9 -> "Sep";
            case 10 -> "Oct";
            case 11 -> "Nov";
            case 12 -> "Dec";
            default -> parts[0];
        } + " " + yearShort;
    }
}
