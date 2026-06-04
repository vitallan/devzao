package com.allanvital.devzao.github;

import java.time.Instant;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record GitHubRateLimitStatus(int remaining, int limit, Instant resetAt) {

    public boolean isExhausted() {
        return remaining <= 0;
    }

}
