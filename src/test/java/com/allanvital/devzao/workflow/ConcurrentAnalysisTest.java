package com.allanvital.devzao.workflow;

import com.allanvital.devzao.async.GitDeepAnalysisService;
import com.allanvital.devzao.domain.GitDeepAnalysisStatus;
import com.allanvital.devzao.domain.GitHubUser;
import com.allanvital.devzao.github.GitHubApiCallCategory;
import com.allanvital.devzao.github.dto.GitHubAvatar;
import com.allanvital.devzao.github.dto.GitHubRepositoryInfo;
import com.allanvital.devzao.github.dto.GitHubUserInfo;
import com.allanvital.devzao.git.TestRepoBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public class ConcurrentAnalysisTest extends E2ETest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final GitHubAvatar PNG_AVATAR = new GitHubAvatar("png".getBytes(UTF_8), "image/png");

    @Autowired
    private GitDeepAnalysisService gitDeepAnalysisService;

    @Test
    public void backgroundAnalysisShouldNotBlockLiveAnalysis() throws Exception {
        String liveLogin = "concurrent-live-user";
        String backgroundLogin = "concurrent-background-user";

        GitHubUserInfo liveInfo = new GitHubUserInfo(700L, liveLogin, "Live User", "live concurrent", 1,
                URI.create("https://example.com/" + liveLogin + ".png"));
        GitHubUserInfo backgroundInfo = new GitHubUserInfo(701L, backgroundLogin, "Background User", "bg concurrent", 2,
                URI.create("https://example.com/" + backgroundLogin + ".png"));

        URI liveRepoUri = TestRepoBuilder.create()
                .commit("2026-01-15T10:30:00Z")
                .build();
        URI backgroundRepoUri = TestRepoBuilder.create()
                .commit("2026-02-20T14:00:00Z")
                .build();

        configureSuccess(liveInfo);
        this.fakeGitHubClient.setRepositoryPage(liveInfo.login(), 1, java.util.List.of(
                repoInfo(liveInfo.login(), 7101L, "live-repo", liveRepoUri)
        ));

        configureSuccess(backgroundInfo);
        this.fakeGitHubClient.setRepositoryPage(backgroundInfo.login(), 1, java.util.List.of(
                repoInfo(backgroundInfo.login(), 7102L, "background-repo", backgroundRepoUri)
        ));

        // Trigger full flow for both users
        this.mockMvc.perform(get("/u/" + liveInfo.login()))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        this.mockMvc.perform(get("/u/" + backgroundInfo.login()))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilFetched(liveInfo.login(), TIMEOUT);
        waitUntilFetched(backgroundInfo.login(), TIMEOUT);
        waitUntilRepositorySyncFetched(liveInfo.login(), TIMEOUT);
        waitUntilRepositorySyncFetched(backgroundInfo.login(), TIMEOUT);

        GitHubUser liveUser = this.gitHubUserRepository.findByLoginLower(liveInfo.login()).orElseThrow();
        GitHubUser backgroundUser = this.gitHubUserRepository.findByLoginLower(backgroundInfo.login()).orElseThrow();

        // Reset analysis status and enqueue with different categories
        liveUser.setDeepAnalysisStatus(null);
        this.gitHubUserRepository.save(liveUser);
        backgroundUser.setDeepAnalysisStatus(null);
        this.gitHubUserRepository.save(backgroundUser);

        this.gitDeepAnalysisService.startAnalysis(liveUser.getId(), GitHubApiCallCategory.LIVE);
        this.gitDeepAnalysisService.startAnalysis(backgroundUser.getId(), GitHubApiCallCategory.BACKGROUND);

        waitUntilDeepAnalysisCompleted(liveInfo.login(), TIMEOUT);
        waitUntilDeepAnalysisCompleted(backgroundInfo.login(), TIMEOUT);

        liveUser = this.gitHubUserRepository.findByLoginLower(liveInfo.login()).orElseThrow();
        backgroundUser = this.gitHubUserRepository.findByLoginLower(backgroundInfo.login()).orElseThrow();

        assertEquals(GitDeepAnalysisStatus.COMPLETED, liveUser.getDeepAnalysisStatus(),
                "Live user deep analysis should complete");
        assertEquals(GitDeepAnalysisStatus.COMPLETED, backgroundUser.getDeepAnalysisStatus(),
                "Background user deep analysis should complete");
    }

    private void configureSuccess(GitHubUserInfo info) {
        this.fakeGitHubClient.setUserSuccess(info.login(), info);
        this.fakeGitHubClient.setAvatarSuccess(info.avatarUrl(), PNG_AVATAR);
    }

    private GitHubRepositoryInfo repoInfo(String ownerLogin, long githubRepoId, String name, URI cloneUrl) {
        return new GitHubRepositoryInfo(
                githubRepoId, name, ownerLogin + "/" + name, "Repository " + name,
                URI.create("https://github.com/" + ownerLogin + "/" + name),
                cloneUrl, false, 1, 0, 1, "Java", false,
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
