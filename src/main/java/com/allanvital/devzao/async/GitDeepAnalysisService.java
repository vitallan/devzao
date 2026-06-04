package com.allanvital.devzao.async;

import com.allanvital.devzao.Constants;
import com.allanvital.devzao.config.GitConfig;
import com.allanvital.devzao.domain.*;
import com.allanvital.devzao.github.GitHubApiCallCategory;
import com.allanvital.devzao.git.CommitInfo;
import com.allanvital.devzao.git.GitCommitWalkResult;
import com.allanvital.devzao.git.remote.JGitCommitWalk;
import org.springframework.jms.core.JmsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Service
public class GitDeepAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(GitDeepAnalysisService.class);

    private final GitHubUserRepository gitHubUserRepository;
    private final GitHubRepositoryRepository gitHubRepositoryRepository;
    private final GitCommitAnalysisRepository gitCommitAnalysisRepository;
    private final GitMonthlyActivityRepository gitMonthlyActivityRepository;
    private final GitHourlyActivityRepository gitHourlyActivityRepository;
    private final GitWeekdayActivityRepository gitWeekdayActivityRepository;
    private final JGitCommitWalk gitCommitWalk;
    private final GitConfig gitConfig;
    private final JmsTemplate jmsTemplate;

    public GitDeepAnalysisService(
            GitHubUserRepository gitHubUserRepository,
            GitHubRepositoryRepository gitHubRepositoryRepository,
            GitCommitAnalysisRepository gitCommitAnalysisRepository,
            GitMonthlyActivityRepository gitMonthlyActivityRepository,
            GitHourlyActivityRepository gitHourlyActivityRepository,
            GitWeekdayActivityRepository gitWeekdayActivityRepository,
            JGitCommitWalk gitCommitWalk,
            GitConfig gitConfig,
            JmsTemplate jmsTemplate
    ) {
        this.gitHubUserRepository = gitHubUserRepository;
        this.gitHubRepositoryRepository = gitHubRepositoryRepository;
        this.gitCommitAnalysisRepository = gitCommitAnalysisRepository;
        this.gitMonthlyActivityRepository = gitMonthlyActivityRepository;
        this.gitHourlyActivityRepository = gitHourlyActivityRepository;
        this.gitWeekdayActivityRepository = gitWeekdayActivityRepository;
        this.gitCommitWalk = gitCommitWalk;
        this.gitConfig = gitConfig;
        this.jmsTemplate = jmsTemplate;
    }

    public void startAnalysis(Long gitHubUserId) {
        startAnalysis(gitHubUserId, GitHubApiCallCategory.LIVE);
    }

    public void startAnalysis(Long gitHubUserId, GitHubApiCallCategory category) {
        log.info("Enqueuing deep analysis for user id={} category={}", gitHubUserId, category);
        Optional<GitHubUser> userOpt = this.gitHubUserRepository.findById(gitHubUserId);
        if (userOpt.isEmpty()) {
            return;
        }
        GitHubUser user = userOpt.get();
        user.setDeepAnalysisStatus(GitDeepAnalysisStatus.ANALYZING);
        this.gitHubUserRepository.save(user);
        GitDeepAnalysisMessage message = new GitDeepAnalysisMessage(gitHubUserId, category);
        String queue = category == GitHubApiCallCategory.LIVE
                ? Constants.USER_DEEP_ANALYSIS_LIVE_QUEUE
                : Constants.USER_DEEP_ANALYSIS_BACKGROUND_QUEUE;
        this.jmsTemplate.convertAndSend(queue, message);
    }

    @Transactional
    public void analyze(Long gitHubUserId) {
        log.info("Analyzing commits for user id={}", gitHubUserId);
        Optional<GitHubUser> userOpt = this.gitHubUserRepository.findById(gitHubUserId);
        if (userOpt.isEmpty()) {
            log.warn("User id={} not found for deep analysis", gitHubUserId);
            return;
        }
        GitHubUser user = userOpt.get();

        List<GitHubRepository> repos = this.gitHubRepositoryRepository.findByGitHubUser_IdAndIsPrivateFalse(gitHubUserId);
        int depth = this.gitConfig.getCloneDepth();
        Path tempPath = Path.of(this.gitConfig.getCloneTempPath());

        log.info("Started reading repos from user id={} login={}", gitHubUserId, user.getLogin());

        int totalCommits = 0;
        int totalMergeCommits = 0;
        int analyzedRepos = 0;
        boolean anyTruncated = false;
        Map<String, Integer> monthlyCounts = new HashMap<>();
        int[] hourlyCounts = new int[24];
        int[] weekdayCounts = new int[7];

        for (GitHubRepository repo : repos) {
            String cloneUrl = repo.getCloneUrl();
            if (cloneUrl == null || cloneUrl.isBlank()) {
                continue;
            }
            try {
                GitCommitWalkResult result = this.gitCommitWalk.walkCommits(
                        URI.create(cloneUrl),
                        depth,
                        tempPath,
                        "repo-" + repo.getGithubRepoId()
                );
                anyTruncated = anyTruncated || result.truncated();
                int repoCommitCount = 0;
                for (CommitInfo commit : result.commits()) {
                    totalCommits++;
                    repoCommitCount++;
                    if (commit.isMerge()) {
                        totalMergeCommits++;
                    }
                    ZonedDateTime zdt = Instant.ofEpochSecond(commit.epochSecond()).atZone(ZoneId.of("UTC"));
                    String yearMonth = zdt.getYear() + "-" + String.format("%02d", zdt.getMonthValue());
                    monthlyCounts.merge(yearMonth, 1, Integer::sum);
                    hourlyCounts[zdt.getHour()]++;
                    weekdayCounts[zdt.getDayOfWeek().getValue() - 1]++;
                }
                analyzedRepos++;
                repo.setCommitCount(repoCommitCount);
            } catch (Exception e) {
                log.warn("Failed to analyze repo {}: {}", cloneUrl, e.getMessage());
                repo.setCommitCount(0);
            }
        }

        log.info("Read {} commits from {} repositories from user id={} login={}", totalCommits, analyzedRepos, gitHubUserId, user.getLogin());
        saveAnalysis(user, totalCommits, totalMergeCommits, anyTruncated, monthlyCounts, hourlyCounts, weekdayCounts);
    }

    protected void saveAnalysis(GitHubUser user, int totalCommits, int totalMergeCommits,
                                 boolean truncated, Map<String, Integer> monthlyCounts, int[] hourlyCounts, int[] weekdayCounts) {
        Long userId = user.getId();

        this.gitMonthlyActivityRepository.deleteByGitHubUser_Id(userId);
        this.gitHourlyActivityRepository.deleteByGitHubUser_Id(userId);
        this.gitWeekdayActivityRepository.deleteByGitHubUser_Id(userId);

        Optional<GitCommitAnalysis> existingOpt = this.gitCommitAnalysisRepository.findByGitHubUser_Id(userId);
        GitCommitAnalysis analysis = existingOpt.orElseGet(() -> new GitCommitAnalysis(user));
        analysis.setTotalCommits(totalCommits);
        analysis.setTotalMergeCommits(totalMergeCommits);
        analysis.setTruncated(truncated);
        this.gitCommitAnalysisRepository.save(analysis);

        for (Map.Entry<String, Integer> entry : monthlyCounts.entrySet()) {
            this.gitMonthlyActivityRepository.save(new GitMonthlyActivity(user, entry.getKey(), entry.getValue()));
        }

        for (int hour = 0; hour < 24; hour++) {
            int count = hourlyCounts[hour];
            if (count > 0) {
                this.gitHourlyActivityRepository.save(new GitHourlyActivity(user, hour, count));
            }
        }

        for (int day = 0; day < 7; day++) {
            int count = weekdayCounts[day];
            if (count > 0) {
                this.gitWeekdayActivityRepository.save(new GitWeekdayActivity(user, day, count));
            }
        }

        user.setDeepAnalysisStatus(GitDeepAnalysisStatus.COMPLETED);
        this.gitHubUserRepository.save(user);

        log.info("Deep analysis completed for user id={}: {} commits, {} months, truncated={}",
                userId, totalCommits, monthlyCounts.size(), truncated);
    }

    @Transactional
    public void markFailed(Long gitHubUserId) {
        Optional<GitHubUser> userOpt = this.gitHubUserRepository.findById(gitHubUserId);
        if (userOpt.isEmpty()) {
            return;
        }
        GitHubUser user = userOpt.get();
        user.setDeepAnalysisStatus(GitDeepAnalysisStatus.FAILED);
        this.gitHubUserRepository.save(user);
    }
}
