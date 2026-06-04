package com.allanvital.devzao.async;

import com.allanvital.devzao.domain.GitCommitAnalysis;
import com.allanvital.devzao.domain.GitCommitAnalysisRepository;
import com.allanvital.devzao.domain.GitDeepAnalysisStatus;
import com.allanvital.devzao.domain.GitHubUser;
import com.allanvital.devzao.domain.GitHubUserRepository;
import com.allanvital.devzao.domain.GitHubUserStatus;
import com.allanvital.devzao.github.GitHubApiCallCategory;
import com.allanvital.devzao.github.GitHubRateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Component
public class BackgroundRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackgroundRefreshScheduler.class);

    private final GitHubUserRepository gitHubUserRepository;
    private final GitHubRateLimitService gitHubRateLimitService;
    private final LiveUserRequestService liveUserRequestService;
    private final GitHubRepositorySyncService gitHubRepositorySyncService;
    private final GitDeepAnalysisService gitDeepAnalysisService;
    private final GitCommitAnalysisRepository gitCommitAnalysisRepository;

    private final Duration userInterval;
    private final Duration repoInterval;
    private final Duration analysisInterval;
    private final int batchSize;

    public BackgroundRefreshScheduler(
            GitHubUserRepository gitHubUserRepository,
            GitHubRateLimitService gitHubRateLimitService,
            LiveUserRequestService liveUserRequestService,
            GitHubRepositorySyncService gitHubRepositorySyncService,
            GitDeepAnalysisService gitDeepAnalysisService,
            GitCommitAnalysisRepository gitCommitAnalysisRepository,
            @Value("${devzao.refresh.user-interval:24h}") Duration userInterval,
            @Value("${devzao.refresh.repo-interval:24h}") Duration repoInterval,
            @Value("${devzao.refresh.analysis-interval:7d}") Duration analysisInterval,
            @Value("${devzao.refresh.batch-size:10}") int batchSize
    ) {
        this.gitHubUserRepository = gitHubUserRepository;
        this.gitHubRateLimitService = gitHubRateLimitService;
        this.liveUserRequestService = liveUserRequestService;
        this.gitHubRepositorySyncService = gitHubRepositorySyncService;
        this.gitDeepAnalysisService = gitDeepAnalysisService;
        this.gitCommitAnalysisRepository = gitCommitAnalysisRepository;
        this.userInterval = userInterval;
        this.repoInterval = repoInterval;
        this.analysisInterval = analysisInterval;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedRateString = "${devzao.refresh.poll-interval:3600000}")
    public void refreshStaleData() {
        log.debug("Running background refresh check");
        Instant now = Instant.now();

        List<GitHubUser> staleUsers = this.gitHubUserRepository.findStaleForRefresh(
                now.minus(this.userInterval),
                now.minus(this.repoInterval),
                GitDeepAnalysisStatus.COMPLETED,
                GitHubUserStatus.NEW,
                PageRequest.ofSize(this.batchSize)
        );

        int acquired = 0;
        for (GitHubUser user : staleUsers) {
            if (!this.gitHubRateLimitService.tryAcquire(GitHubApiCallCategory.BACKGROUND)) {
                log.info("Rate limited, stopping background refresh after {} users", acquired);
                break;
            }

            boolean staleUser = user.getFetchedAt() == null
                    || user.getFetchedAt().isBefore(now.minus(this.userInterval));
            boolean staleRepos = user.getRepositorySyncedAt() == null
                    || user.getRepositorySyncedAt().isBefore(now.minus(this.repoInterval));

            if (staleUser) {
                this.liveUserRequestService.enqueueRefresh(user.getId(), GitHubApiCallCategory.BACKGROUND);
                log.info("Enqueued full background refresh for user id={} login={}", user.getId(), user.getLogin());
            } else if (staleRepos) {
                this.gitHubRepositorySyncService.startSync(user, GitHubApiCallCategory.BACKGROUND);
                log.info("Enqueued background repo sync for user id={} login={}", user.getId(), user.getLogin());
            } else if (isAnalysisStale(user, now)) {
                this.gitDeepAnalysisService.startAnalysis(user.getId(), GitHubApiCallCategory.BACKGROUND);
                log.info("Enqueued background analysis refresh for user id={} login={}", user.getId(), user.getLogin());
            }

            acquired++;
        }

        if (acquired > 0) {
            log.info("Background refresh enqueued {} stale users", acquired);
        }
    }

    private boolean isAnalysisStale(GitHubUser user, Instant now) {
        if (user.getDeepAnalysisStatus() == GitDeepAnalysisStatus.ANALYZING) {
            return false;
        }
        if (user.getDeepAnalysisStatus() != GitDeepAnalysisStatus.COMPLETED) {
            return true;
        }
        Optional<GitCommitAnalysis> analysisOpt = this.gitCommitAnalysisRepository.findByGitHubUser_Id(user.getId());
        return analysisOpt.isEmpty()
                || analysisOpt.get().getAnalyzedAt().isBefore(now.minus(this.analysisInterval));
    }
}
