package com.allanvital.devzao.workflow;

import com.allanvital.devzao.async.BackgroundRefreshScheduler;
import com.allanvital.devzao.domain.GitDeepAnalysisStatus;
import com.allanvital.devzao.domain.GitHubUser;
import com.allanvital.devzao.domain.GitHubUserStatus;
import com.allanvital.devzao.github.dto.GitHubAvatar;
import com.allanvital.devzao.github.dto.GitHubFollower;
import com.allanvital.devzao.github.dto.GitHubRepositoryInfo;
import com.allanvital.devzao.github.dto.GitHubUserInfo;
import com.allanvital.devzao.git.TestRepoBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public class BackgroundRefreshTest extends E2ETest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final GitHubAvatar PNG_AVATAR = new GitHubAvatar("png".getBytes(UTF_8), "image/png");

    @Autowired
    private BackgroundRefreshScheduler backgroundRefreshScheduler;

    @Test
    public void shouldRefreshStaleUser() throws Exception {
        String login = "refresh-stale-user";
        GitHubUserInfo info = new GitHubUserInfo(420L, login, "Refresh Stale", "stale scenario", 5,
                URI.create("https://example.com/" + login + ".png"));
        configureSuccess(info);
        setUpRepo(info.login(), 8201L, "stale-repo", "2026-04-01T12:00:00Z");

        triggerAndWaitForFullCompletion(info);

        // Make user stale
        GitHubUser user = gitHubUserRepository.findByLoginLower(login).orElseThrow();
        Instant staleFetchedAt = Instant.now().minus(Duration.ofHours(48));
        user.setFetchedAt(staleFetchedAt);
        gitHubUserRepository.save(user);

        // Call scheduler
        backgroundRefreshScheduler.refreshStaleData();

        // Verify enqueueRefresh was called (status set to STALE synchronously)
        user = gitHubUserRepository.findByLoginLower(login).orElseThrow();
        assertEquals(GitHubUserStatus.STALE, user.getStatus());

        // Wait for the full cascade to complete
        waitUntilFetched(login, TIMEOUT);
        waitUntilRepositorySyncFetched(login, TIMEOUT);
        waitUntilDeepAnalysisCompleted(login, TIMEOUT);

        // Verify fresh data
        user = gitHubUserRepository.findByLoginLower(login).orElseThrow();
        assertTrue(user.getFetchedAt().isAfter(staleFetchedAt), "fetchedAt should be refreshed");
    }

    @Test
    public void shouldOnlySyncReposWhenUserFresh() throws Exception {
        String login = "refresh-repo-user";
        GitHubUserInfo info = new GitHubUserInfo(422L, login, "Refresh Repo", "repo scenario", 6,
                URI.create("https://example.com/" + login + ".png"));
        configureSuccess(info);
        setUpRepo(info.login(), 8202L, "repo-sync-repo", "2026-04-02T12:00:00Z");

        triggerAndWaitForFullCompletion(info);

        // Make repos stale but keep user data fresh
        GitHubUser user = gitHubUserRepository.findByLoginLower(login).orElseThrow();
        user.setRepositorySyncedAt(Instant.now().minus(Duration.ofHours(48)));
        gitHubUserRepository.save(user);
        Instant freshFetchedAt = user.getFetchedAt();

        // Call scheduler
        backgroundRefreshScheduler.refreshStaleData();

        // Status should remain FETCHED (not STALE) because user data is fresh
        user = gitHubUserRepository.findByLoginLower(login).orElseThrow();
        assertEquals(GitHubUserStatus.FETCHED, user.getStatus());

        // Wait for repo sync to re-complete
        waitUntilRepositorySyncFetched(login, TIMEOUT);

        // Analysis should also complete (cascaded from repo sync)
        waitUntilDeepAnalysisCompleted(login, TIMEOUT);

        // Verify fetchedAt was NOT refreshed
        user = gitHubUserRepository.findByLoginLower(login).orElseThrow();
        assertEquals(freshFetchedAt, user.getFetchedAt());
    }

    @Test
    public void shouldOnlyAnalyzeWhenReposFresh() throws Exception {
        String login = "refresh-analysis-user";
        GitHubUserInfo info = new GitHubUserInfo(424L, login, "Refresh Analysis", "analysis scenario", 7,
                URI.create("https://example.com/" + login + ".png"));
        configureSuccess(info);
        setUpRepo(info.login(), 8203L, "analysis-repo", "2026-04-03T12:00:00Z");

        triggerAndWaitForFullCompletion(info);

        // Set analysis back to NOT_STARTED (as if never completed)
        GitHubUser user = gitHubUserRepository.findByLoginLower(login).orElseThrow();
        user.setDeepAnalysisStatus(GitDeepAnalysisStatus.NOT_STARTED);
        gitHubUserRepository.save(user);

        // Call scheduler
        backgroundRefreshScheduler.refreshStaleData();

        // Wait for analysis to complete
        waitUntilDeepAnalysisCompleted(login, TIMEOUT);

        // Verify it was re-run
        user = gitHubUserRepository.findByLoginLower(login).orElseThrow();
        assertEquals(GitDeepAnalysisStatus.COMPLETED, user.getDeepAnalysisStatus());
    }

    @Test
    public void shouldSkipWhenRateLimited() throws Exception {
        String login = "rate-limited-user";
        GitHubUserInfo info = new GitHubUserInfo(426L, login, "Rate Limited", "rate scenario", 8,
                URI.create("https://example.com/" + login + ".png"));
        configureSuccess(info);
        setUpRepo(info.login(), 8204L, "rate-repo", "2026-04-04T12:00:00Z");

        triggerAndWaitForFullCompletion(info);

        // Make user stale
        GitHubUser user = gitHubUserRepository.findByLoginLower(login).orElseThrow();
        user.setFetchedAt(Instant.now().minus(Duration.ofHours(48)));
        gitHubUserRepository.save(user);

        // Exhaust rate limit for BACKGROUND category
        gitHubRateLimitService.recordResponse(0, 100, Instant.now().plusSeconds(3600).getEpochSecond());

        // Call scheduler — should skip due to rate limit
        backgroundRefreshScheduler.refreshStaleData();

        // Status should still be FETCHED (scheduler did nothing)
        user = gitHubUserRepository.findByLoginLower(login).orElseThrow();
        assertEquals(GitHubUserStatus.FETCHED, user.getStatus());
    }

    @Test
    public void shouldSkipUserWhenAlreadyAnalyzing() throws Exception {
        String login = "already-analyzing-user";
        GitHubUserInfo info = new GitHubUserInfo(428L, login, "Already Analyzing", "analyzing scenario", 9,
                URI.create("https://example.com/" + login + ".png"));
        configureSuccess(info);
        setUpRepo(info.login(), 8205L, "analyzing-repo", "2026-04-05T12:00:00Z");

        triggerAndWaitForFullCompletion(info);

        // Set analysis to ANALYZING to simulate in-progress
        GitHubUser user = gitHubUserRepository.findByLoginLower(login).orElseThrow();
        user.setDeepAnalysisStatus(GitDeepAnalysisStatus.ANALYZING);
        gitHubUserRepository.save(user);

        // Call scheduler
        backgroundRefreshScheduler.refreshStaleData();

        // Verify status is still ANALYZING (not re-enqueued)
        user = gitHubUserRepository.findByLoginLower(login).orElseThrow();
        assertEquals(GitDeepAnalysisStatus.ANALYZING, user.getDeepAnalysisStatus());
    }

    @Test
    public void shouldNotRefreshNewUsers() throws Exception {
        String login = "new-user-from-propagation";
        GitHubUser created = this.gitHubUserRepository.save(
                new GitHubUser(login, login.toLowerCase(), GitHubUserStatus.NEW)
        );

        backgroundRefreshScheduler.refreshStaleData();

        GitHubUser user = this.gitHubUserRepository.findByLoginLower(login).orElseThrow();
        assertEquals(GitHubUserStatus.NEW, user.getStatus(),
                "Scheduler should not touch NEW users — they are handled by their own enqueued message");
    }

    @Test
    public void backgroundRefreshShouldNotTriggerFollowerPropagation() throws Exception {
        String login = "no-follower-propagation";
        GitHubUserInfo info = new GitHubUserInfo(430L, login, "No Follower Prop", "guardrail scenario", 15,
                URI.create("https://example.com/" + login + ".png"));
        configureSuccess(info);
        setUpRepo(info.login(), 8206L, "guardrail-repo", "2026-06-01T12:00:00Z");

        triggerAndWaitForFullCompletion(info);

        // Configure a follower that does not exist in DB yet
        String followerLogin = "should-not-be-created-by-background";
        this.fakeGitHubClient.setFollowerPage(info.login(), 1, List.of(
                new GitHubFollower(999001L, followerLogin)
        ));

        // Make user stale so background refresh triggers full cascade
        GitHubUser user = gitHubUserRepository.findByLoginLower(login).orElseThrow();
        user.setFetchedAt(Instant.now().minus(Duration.ofHours(48)));
        gitHubUserRepository.save(user);

        // Call scheduler — cascade goes through BACKGROUND category entirely
        backgroundRefreshScheduler.refreshStaleData();

        // Wait for the full background cascade
        waitUntilFetched(login, TIMEOUT);
        waitUntilRepositorySyncFetched(login, TIMEOUT);
        waitUntilDeepAnalysisCompleted(login, TIMEOUT);

        // Verify that follower was NOT created (background path must not trigger propagation)
        assertTrue(this.gitHubUserRepository.findByLoginLower(followerLogin).isEmpty(),
                "Background refresh should not trigger follower propagation");
    }

    private void triggerAndWaitForFullCompletion(GitHubUserInfo info) throws Exception {
        mockMvc.perform(get("/u/" + info.login()))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilFetched(info.login(), TIMEOUT);
        waitUntilRepositorySyncFetched(info.login(), TIMEOUT);
        waitUntilDeepAnalysisCompleted(info.login(), TIMEOUT);
    }

    private void setUpRepo(String ownerLogin, long githubRepoId, String name, String isoDateTime) {
        URI repoUri = TestRepoBuilder.create()
                .commit(isoDateTime)
                .build();
        GitHubRepositoryInfo repo = new GitHubRepositoryInfo(
                githubRepoId, name, ownerLogin + "/" + name, "Repository " + name,
                URI.create("https://github.com/" + ownerLogin + "/" + name),
                repoUri, false, 1, 0, 1, "Java", false,
                Instant.parse("2025-06-01T00:00:00Z"),
                Instant.parse("2025-06-15T00:00:00Z"),
                Instant.parse("2025-07-01T00:00:00Z"),
                false
        );
        fakeGitHubClient.setRepositoryPage(ownerLogin, 1, List.of(repo));
    }

    private void configureSuccess(GitHubUserInfo info) {
        fakeGitHubClient.setUserSuccess(info.login(), info);
        fakeGitHubClient.setAvatarSuccess(info.avatarUrl(), PNG_AVATAR);
    }

    private void waitUntilDeepAnalysisCompleted(String username, Duration timeout) {
        long sleepTime = 100L;
        Instant timeoutAt = Instant.now().plus(timeout);
        do {
            var userOpt = gitHubUserRepository.findByLoginLower(username.toLowerCase());
            if (userOpt.isPresent() && userOpt.get().getDeepAnalysisStatus() == GitDeepAnalysisStatus.COMPLETED) {
                return;
            }
            sleep(sleepTime);
        } while (Instant.now().isBefore(timeoutAt));
        fail("User " + username + " deep analysis did not complete in time");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
