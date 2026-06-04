package com.allanvital.devzao.workflow;

import com.allanvital.devzao.domain.GitHubUser;
import com.allanvital.devzao.domain.GitHubRepositoryRepository;
import com.allanvital.devzao.domain.GitHubRepositorySyncStatus;
import com.allanvital.devzao.domain.GitHubUserPhotoRepository;
import com.allanvital.devzao.domain.GitHubUserRepository;
import com.allanvital.devzao.domain.GitHubUserStatus;
import com.allanvital.devzao.async.GitHubRepositorySyncService;
import com.allanvital.devzao.github.FakeGitHubClient;
import com.allanvital.devzao.github.FakeGithubClientConfiguration;
import com.allanvital.devzao.github.GitHubRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeGithubClientConfiguration.class)
public abstract class E2ETest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected FakeGitHubClient fakeGitHubClient;

    @Autowired
    protected GitHubUserRepository gitHubUserRepository;

    @Autowired
    protected GitHubUserPhotoRepository gitHubUserPhotoRepository;

    @Autowired
    protected GitHubRepositoryRepository gitHubRepositoryRepository;

    @Autowired
    protected GitHubRepositorySyncService gitHubRepositorySyncService;

    @Autowired
    protected GitHubRateLimitService gitHubRateLimitService;

    @BeforeEach
    public void resetRateLimitService() {
        this.gitHubRateLimitService.reset();
    }

    protected void waitUntilFetched(String username, Duration timeout) {
        waitUntilStatus(username, timeout, GitHubUserStatus.FETCHED);
    }

    protected void waitUntilNotFound(String username, Duration timeout) {
        waitUntilStatus(username, timeout, GitHubUserStatus.NOT_FOUND);
    }

    protected void waitUntilFailed(String username, Duration timeout) {
        waitUntilStatus(username, timeout, GitHubUserStatus.FAILED);
    }

    protected void waitUntilStatus(String username, Duration timeout, GitHubUserStatus status) {
        long sleepTime = 100L;
        Instant timeoutAt = Instant.now().plus(timeout);
        do {
            Optional<GitHubUser> githubUser = gitHubUserRepository.findByLoginLower(username.toLowerCase());
            if (githubUser.isEmpty() || !status.equals(githubUser.get().getStatus())) {
                sleep(sleepTime);
            } else {
                return;
            }
        } while (Instant.now().isBefore(timeoutAt));
        fail("user " + username + " did not reach " + status + " in time");
    }

    protected void waitUntilRepositorySyncFetched(String username, Duration timeout) {
        waitUntilRepositorySyncStatus(username, timeout, GitHubRepositorySyncStatus.FETCHED);
    }

    protected void waitUntilRepositorySyncStatus(String username, Duration timeout, GitHubRepositorySyncStatus status) {
        long sleepTime = 100L;
        Instant timeoutAt = Instant.now().plus(timeout);
        do {
            Optional<GitHubUser> githubUser = gitHubUserRepository.findByLoginLower(username.toLowerCase());
            if (githubUser.isEmpty() || !status.equals(githubUser.get().getRepositorySyncStatus())) {
                sleep(sleepTime);
            } else {
                return;
            }
        } while (Instant.now().isBefore(timeoutAt));
        fail("user " + username + " repository sync did not reach " + status + " in time");
    }

    protected void waitUntilRepositoryCount(Long gitHubUserId, long count, Duration timeout) {
        long sleepTime = 100L;
        Instant timeoutAt = Instant.now().plus(timeout);
        do {
            long actualCount = this.gitHubRepositoryRepository.countByGitHubUser_Id(gitHubUserId);
            if (actualCount != count) {
                sleep(sleepTime);
            } else {
                return;
            }
        } while (Instant.now().isBefore(timeoutAt));
        fail("user id " + gitHubUserId + " repository count did not reach " + count + " in time");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
