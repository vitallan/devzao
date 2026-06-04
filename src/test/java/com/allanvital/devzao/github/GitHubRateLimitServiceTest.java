package com.allanvital.devzao.github;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
* @author Allan Vital (https://allanvital.com)
  */
public class GitHubRateLimitServiceTest {

    @Test
    public void shouldAllowAllCallsWhenUnknown() {
        GitHubRateLimitService service = new GitHubRateLimitService(30);
        assertTrue(service.tryAcquire(GitHubApiCallCategory.LIVE));
        assertTrue(service.tryAcquire(GitHubApiCallCategory.BACKGROUND));
    }

    @Test
    public void shouldAllowLiveWhenExhausted() {
        GitHubRateLimitService service = new GitHubRateLimitService(30);
        service.recordResponse(0, 60, Instant.now().plusSeconds(300).getEpochSecond());
        assertTrue(service.tryAcquire(GitHubApiCallCategory.LIVE));
    }

    @Test
    public void shouldBlockBackgroundWhenBelowReserve() {
        GitHubRateLimitService service = new GitHubRateLimitService(30);
        service.recordResponse(5, 60, Instant.now().plusSeconds(300).getEpochSecond());
        assertFalse(service.tryAcquire(GitHubApiCallCategory.BACKGROUND));
    }

    @Test
    public void shouldAllowBackgroundWhenAboveReserve() {
        GitHubRateLimitService service = new GitHubRateLimitService(30);
        service.recordResponse(20, 60, Instant.now().plusSeconds(300).getEpochSecond());
        assertTrue(service.tryAcquire(GitHubApiCallCategory.BACKGROUND));
    }

    @Test
    public void shouldAllowBackgroundWhenExactlyAtReserve() {
        GitHubRateLimitService service = new GitHubRateLimitService(30);
        service.recordResponse(18, 60, Instant.now().plusSeconds(300).getEpochSecond());
        assertFalse(service.tryAcquire(GitHubApiCallCategory.BACKGROUND));
    }

    @Test
    public void shouldUpdateStateFromRecordResponse() {
        GitHubRateLimitService service = new GitHubRateLimitService(30);
        long resetEpochSecond = Instant.now().plusSeconds(600).getEpochSecond();
        service.recordResponse(42, 100, resetEpochSecond);

        GitHubRateLimitStatus status = service.getStatus();
        assertEquals(42, status.remaining());
        assertEquals(100, status.limit());
        assertEquals(resetEpochSecond, status.resetAt().getEpochSecond());
        assertFalse(status.isExhausted());
    }

    @Test
    public void shouldMarkExhaustedWhenRemainingZero() {
        GitHubRateLimitService service = new GitHubRateLimitService(30);
        service.recordResponse(0, 60, Instant.now().plusSeconds(300).getEpochSecond());
        assertTrue(service.getStatus().isExhausted());
    }

    @Test
    public void shouldRejectInvalidPercentage() {
        assertThrows(IllegalArgumentException.class, () -> new GitHubRateLimitService(-1));
        assertThrows(IllegalArgumentException.class, () -> new GitHubRateLimitService(101));
        assertDoesNotThrow(() -> new GitHubRateLimitService(0));
        assertDoesNotThrow(() -> new GitHubRateLimitService(100));
    }

    @Test
    public void shouldBlockBackgroundWhenRemainingEqualsReserve() {
        GitHubRateLimitService service = new GitHubRateLimitService(25);
        service.recordResponse(25, 100, Instant.now().plusSeconds(300).getEpochSecond());
        assertFalse(service.tryAcquire(GitHubApiCallCategory.BACKGROUND));
    }

    @Test
    public void shouldAllowBackgroundWhenRemainingJustAboveReserve() {
        GitHubRateLimitService service = new GitHubRateLimitService(25);
        service.recordResponse(26, 100, Instant.now().plusSeconds(300).getEpochSecond());
        assertTrue(service.tryAcquire(GitHubApiCallCategory.BACKGROUND));
    }

}
