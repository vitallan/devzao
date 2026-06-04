package com.allanvital.devzao.async;

import com.allanvital.devzao.Constants;
import com.allanvital.devzao.domain.GitHubUser;
import com.allanvital.devzao.domain.GitHubUserRepository;
import com.allanvital.devzao.domain.GitDeepAnalysisStatus;
import com.allanvital.devzao.github.GitHubApiCallCategory;
import com.allanvital.devzao.github.GitHubClient;
import com.allanvital.devzao.github.GitHubClientException;
import com.allanvital.devzao.github.GitHubClientFailure;
import com.allanvital.devzao.github.GitHubRateLimitService;
import com.allanvital.devzao.github.dto.GitHubRepositoryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Component
public class GitHubRepositorySyncListener {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final GitHubUserRepository gitHubUserRepository;
    private final GitHubClient gitHubClient;
    private final GitHubRepositorySyncService gitHubRepositorySyncService;
    private final GitDeepAnalysisService gitDeepAnalysisService;
    private final GitHubRateLimitService gitHubRateLimitService;

    public GitHubRepositorySyncListener(
            GitHubUserRepository gitHubUserRepository,
            GitHubClient gitHubClient,
            GitHubRepositorySyncService gitHubRepositorySyncService,
            GitDeepAnalysisService gitDeepAnalysisService,
            GitHubRateLimitService gitHubRateLimitService
    ) {
        this.gitHubUserRepository = gitHubUserRepository;
        this.gitHubClient = gitHubClient;
        this.gitHubRepositorySyncService = gitHubRepositorySyncService;
        this.gitDeepAnalysisService = gitDeepAnalysisService;
        this.gitHubRateLimitService = gitHubRateLimitService;
    }

    @JmsListener(destination = Constants.USER_REPOSITORY_SYNC_QUEUE)
    public void onMessage(GitHubRepositorySyncMessage message) {
        GitHubApiCallCategory category = message.category();
        Optional<GitHubUser> userOpt = this.gitHubUserRepository.findById(message.gitHubUserId());
        if (userOpt.isEmpty()) {
            log.info("Ignoring repository sync message for missing user id={}", message.gitHubUserId());
            return;
        }

        GitHubUser user = userOpt.get();
        if (!message.syncToken().equals(user.getRepositorySyncToken())) {
            log.info("Ignoring stale repository sync message for user id={} page={}", message.gitHubUserId(), message.page());
            return;
        }

        try {
            if (!this.gitHubRateLimitService.tryAcquire(category)) {
                Instant retryAfter = this.gitHubRateLimitService.getStatus().resetAt();
                log.info("Sync rate-limited for user id={} page={} category={}, rescheduling until {}",
                        user.getId(), message.page(), category, retryAfter);
                this.gitHubRepositorySyncService.reschedulePage(user.getId(), message.page(), message.syncToken(), retryAfter, category);
                return;
            }

            List<GitHubRepositoryInfo> repositories = this.gitHubClient.fetchRepositories(
                    user.getLogin(),
                    message.page(),
                    Constants.GITHUB_REPOSITORIES_PER_PAGE
            );
            this.gitHubRepositorySyncService.savePage(user, repositories, message.syncToken());

            if (repositories.size() < Constants.GITHUB_REPOSITORIES_PER_PAGE) {
                this.gitHubRepositorySyncService.finalizeSync(user.getId(), message.syncToken());
                this.gitDeepAnalysisService.startAnalysis(user.getId(), category);
                return;
            }

            this.gitHubRepositorySyncService.enqueuePage(user.getId(), message.page() + 1, message.syncToken(), category);
        } catch (GitHubClientException ex) {
            if (ex.getFailure() == GitHubClientFailure.RATE_LIMITED) {
                Instant when = ex.getRetryAfterEpochMillis() == null ? null : Instant.ofEpochMilli(ex.getRetryAfterEpochMillis());
                this.gitHubRepositorySyncService.reschedulePage(user.getId(), message.page(), message.syncToken(), when, category);
                return;
            }

            log.info("Repository sync failed for user id={} page={}: {}", user.getId(), message.page(), ex.getMessage());
            this.gitHubRepositorySyncService.markFailed(user.getId(), message.syncToken());
        } catch (Exception ex) {
            log.info("Unexpected repository sync failure for user id={} page={}", user.getId(), message.page(), ex);
            this.gitHubRepositorySyncService.markFailed(user.getId(), message.syncToken());
        }
    }

}
