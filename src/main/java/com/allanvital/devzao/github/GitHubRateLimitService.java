package com.allanvital.devzao.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Component
public class GitHubRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(GitHubRateLimitService.class);
    private static final int UNKNOWN = Integer.MAX_VALUE;

    private final int liveReservePercentage;

    private final AtomicInteger remaining;
    private final AtomicInteger limit;
    private final AtomicReference<Instant> resetAt;

    public GitHubRateLimitService(
            @Value("${github.rate-limit.live-reserve-percentage:30}") int liveReservePercentage
    ) {
        if (liveReservePercentage < 0 || liveReservePercentage > 100) {
            throw new IllegalArgumentException("liveReservePercentage must be between 0 and 100");
        }
        this.liveReservePercentage = liveReservePercentage;
        this.remaining = new AtomicInteger(UNKNOWN);
        this.limit = new AtomicInteger(UNKNOWN);
        this.resetAt = new AtomicReference<>(Instant.now());
    }

    public boolean tryAcquire(GitHubApiCallCategory category) {
        if (category == GitHubApiCallCategory.LIVE) {
            return true;
        }

        int currentRemaining = this.remaining.get();
        if (currentRemaining == UNKNOWN) {
            return true;
        }

        int currentLimit = this.limit.get();
        int reserve = currentLimit == UNKNOWN ? 0 : (currentLimit * this.liveReservePercentage / 100);

        boolean allowed = currentRemaining > reserve;
        if (!allowed) {
            log.info("Blocking BACKGROUND call: remaining={}, reserve={} ({}% of limit={})",
                    currentRemaining, reserve, this.liveReservePercentage, currentLimit);
        }
        return allowed;
    }

    public void recordResponse(int newRemaining, int newLimit, long resetAtEpochSecond) {
        this.remaining.set(newRemaining);
        this.limit.set(newLimit);
        this.resetAt.set(Instant.ofEpochSecond(resetAtEpochSecond));
        log.debug("Rate limit state updated: remaining={}, limit={}, resetAt={}",
                newRemaining, newLimit, this.resetAt.get());
    }

    public GitHubRateLimitStatus getStatus() {
        return new GitHubRateLimitStatus(this.remaining.get(), this.limit.get(), this.resetAt.get());
    }

    public void reset() {
        this.remaining.set(UNKNOWN);
        this.limit.set(UNKNOWN);
        this.resetAt.set(Instant.now());
        log.debug("Rate limit state reset");
    }

}
