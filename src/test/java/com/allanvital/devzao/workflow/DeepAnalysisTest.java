package com.allanvital.devzao.workflow;

import com.allanvital.devzao.domain.GitDeepAnalysisStatus;
import com.allanvital.devzao.domain.GitHourlyActivity;
import com.allanvital.devzao.domain.GitMonthlyActivity;
import com.allanvital.devzao.domain.GitMonthlyActivityRepository;
import com.allanvital.devzao.domain.GitHourlyActivityRepository;
import com.allanvital.devzao.domain.GitHubUser;
import com.allanvital.devzao.github.dto.GitHubAvatar;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public class DeepAnalysisTest extends E2ETest {

    private static final Duration USER_TIMEOUT = Duration.ofSeconds(5);
    private static final GitHubAvatar PNG_AVATAR = new GitHubAvatar("png".getBytes(UTF_8), "image/png");

    @Autowired
    private GitMonthlyActivityRepository gitMonthlyActivityRepository;

    @Autowired
    private GitHourlyActivityRepository gitHourlyActivityRepository;

    @Test
    public void shouldCompleteDeepAnalysisAndRenderMonthlyAndHourlyCharts() throws Exception {
        String login = "chart-analysis-user";
        GitHubUserInfo info = new GitHubUserInfo(410L, login, "Chart User", "chart scenario", 1,
                URI.create("https://example.com/" + login + ".png"));
        configureSuccess(info);

        URI repoOneUri = TestRepoBuilder.create()
                .commit("2026-01-15T10:30:00Z")
                .commit("2026-02-20T14:15:00Z")
                .commit("2026-02-20T14:30:00Z")
                .commit("2026-03-10T22:00:00Z")
                .build();
        URI repoTwoUri = TestRepoBuilder.create()
                .commit("2026-04-05T03:00:00Z")
                .build();

        GitHubRepositoryInfo repo1 = repositoryInfo(info.login(), 8101L, "repo-one", false, 10, "Java", repoOneUri);
        GitHubRepositoryInfo repo2 = repositoryInfo(info.login(), 8102L, "repo-two", false, 5, "Python", repoTwoUri);
        this.fakeGitHubClient.setRepositoryPage(info.login(), 1, List.of(repo1, repo2));

        this.mockMvc.perform(get("/u/" + info.login()))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilFetched(info.login(), USER_TIMEOUT);
        waitUntilRepositorySyncFetched(info.login(), USER_TIMEOUT);
        waitUntilDeepAnalysisCompleted(info.login(), USER_TIMEOUT);

        GitHubUser user = this.gitHubUserRepository.findByLoginLower(info.login()).orElseThrow();
        assertEquals(GitDeepAnalysisStatus.COMPLETED, user.getDeepAnalysisStatus());

        List<GitMonthlyActivity> monthly = this.gitMonthlyActivityRepository.findByGitHubUser_IdOrderByActivityMonthAsc(user.getId());
        assertEquals(4, monthly.size());
        assertEquals("2026-01", monthly.get(0).getActivityMonth());
        assertEquals(1, monthly.get(0).getCommitCount());
        assertEquals("2026-02", monthly.get(1).getActivityMonth());
        assertEquals(2, monthly.get(1).getCommitCount());

        List<GitHourlyActivity> hourly = this.gitHourlyActivityRepository.findByGitHubUser_IdOrderByHourAsc(user.getId());
        assertEquals(4, hourly.size());

        this.mockMvc.perform(get("/u/" + info.login()))
                .andExpect(content().string(containsString("data-deep-analysis-status=\"COMPLETED\"")))
                .andExpect(content().string(containsString("Commits per month")))
                .andExpect(content().string(containsString("Preferred Committing Hour (24h)")))
                .andExpect(status().isOk());

        this.mockMvc.perform(get("/u/" + info.login() + "/activity"))
                .andExpect(content().string(containsString("data-deep-analysis-status=\"COMPLETED\"")))
                .andExpect(content().string(containsString("Commits per month")))
                .andExpect(content().string(containsString("Preferred Committing Hour (24h)")))
                .andExpect(content().string(containsString("class=\"bar-chart monthly-chart\"")))
                .andExpect(content().string(containsString("class=\"bar-chart hourly-chart\"")))
                .andExpect(status().isOk());
    }

    @Test
    public void shouldShowAnalyzingState() throws Exception {
        String login = "analyzing-status-user";
        GitHubUserInfo info = new GitHubUserInfo(412L, login, "Analyzing Status", "analyzing scenario", 2,
                URI.create("https://example.com/" + login + ".png"));
        URI repoUri = TestRepoBuilder.create()
                .commit("2026-04-01T12:00:00Z")
                .build();
        this.fakeGitHubClient.setUserSuccess(info.login(), info);
        this.fakeGitHubClient.setAvatarSuccess(info.avatarUrl(), PNG_AVATAR);
        this.fakeGitHubClient.setRepositoryPage(info.login(), 1, List.of(
                repositoryInfo(info.login(), 8103L, "analyzing-repo", false, 3, "Go", repoUri)
        ));

        this.mockMvc.perform(get("/u/" + info.login()))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilFetched(info.login(), USER_TIMEOUT);
        waitUntilRepositorySyncFetched(info.login(), USER_TIMEOUT);

        GitHubUser user = this.gitHubUserRepository.findByLoginLower(info.login()).orElseThrow();
        user.setDeepAnalysisStatus(GitDeepAnalysisStatus.ANALYZING);
        this.gitHubUserRepository.save(user);

        this.mockMvc.perform(get("/u/" + info.login() + "/activity"))
                .andExpect(content().string(containsString("ANALYZING")))
                .andExpect(content().string(containsString("Commit activity is being analyzed")))
                .andExpect(status().isOk());
    }

    @Test
    public void shouldRenderEmptyStateWhenNoRepos() throws Exception {
        String login = "empty-analysis-user";
        GitHubUserInfo info = new GitHubUserInfo(414L, login, "Empty Analysis", "empty scenario", 3,
                URI.create("https://example.com/" + login + ".png"));
        configureSuccess(info);
        this.fakeGitHubClient.setRepositoryPage(info.login(), 1, List.of());

        this.mockMvc.perform(get("/u/" + info.login()))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilFetched(info.login(), USER_TIMEOUT);
        waitUntilRepositorySyncFetched(info.login(), USER_TIMEOUT);
        waitUntilDeepAnalysisCompleted(info.login(), USER_TIMEOUT);

        this.mockMvc.perform(get("/u/" + info.login()))
                .andExpect(content().string(containsString("data-deep-analysis-status=\"COMPLETED\"")))
                .andExpect(content().string(containsString("Commits per month")))
                .andExpect(status().isOk());
    }

    @Test
    public void shouldRenderSourceNoteWithCloneDepth() throws Exception {
        String login = "source-note-user";
        GitHubUserInfo info = new GitHubUserInfo(416L, login, "Source Note", "source scenario", 4,
                URI.create("https://example.com/" + login + ".png"));
        URI repoUri = TestRepoBuilder.create()
                .commit("2026-05-01T12:00:00Z")
                .build();
        configureSuccess(info);
        this.fakeGitHubClient.setRepositoryPage(info.login(), 1, List.of(
                repositoryInfo(info.login(), 8104L, "source-repo", false, 1, "Rust", repoUri)
        ));

        this.mockMvc.perform(get("/u/" + info.login()))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilFetched(info.login(), USER_TIMEOUT);
        waitUntilRepositorySyncFetched(info.login(), USER_TIMEOUT);
        waitUntilDeepAnalysisCompleted(info.login(), USER_TIMEOUT);

        this.mockMvc.perform(get("/u/" + info.login()))
                .andExpect(content().string(containsString("Last analyzed:")))
                .andExpect(content().string(containsString("Analysis based on the last")))
                .andExpect(content().string(containsString("800")))
                .andExpect(status().isOk());
    }

    private void configureSuccess(GitHubUserInfo info) {
        this.fakeGitHubClient.setUserSuccess(info.login(), info);
        this.fakeGitHubClient.setAvatarSuccess(info.avatarUrl(), PNG_AVATAR);
    }

    private GitHubRepositoryInfo repositoryInfo(String ownerLogin, long githubRepoId, String name, boolean fork, int stargazersCount, String language) {
        URI defaultCloneUrl = URI.create("https://github.com/" + ownerLogin + "/" + name + ".git");
        return repositoryInfo(ownerLogin, githubRepoId, name, fork, stargazersCount, language, defaultCloneUrl);
    }

    private GitHubRepositoryInfo repositoryInfo(String ownerLogin, long githubRepoId, String name, boolean fork, int stargazersCount, String language, URI cloneUrl) {
        return new GitHubRepositoryInfo(
                githubRepoId,
                name,
                ownerLogin + "/" + name,
                "Repository " + name,
                URI.create("https://github.com/" + ownerLogin + "/" + name),
                cloneUrl,
                fork,
                stargazersCount,
                3,
                stargazersCount,
                language,
                false,
                Instant.parse("2025-06-01T00:00:00Z"),
                Instant.parse("2025-06-15T00:00:00Z"),
                Instant.parse("2025-07-01T00:00:00Z"),
                false
        );
    }

    private void waitUntilDeepAnalysisCompleted(String username, Duration timeout) {
        long sleepTime = 100L;
        Instant timeoutAt = Instant.now().plus(timeout);
        do {
            var userOpt = this.gitHubUserRepository.findByLoginLower(username.toLowerCase());
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
