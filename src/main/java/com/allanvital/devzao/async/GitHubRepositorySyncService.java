package com.allanvital.devzao.async;

import com.allanvital.devzao.Constants;
import com.allanvital.devzao.domain.GitHubRepository;
import com.allanvital.devzao.domain.GitHubRepositoryRepository;
import com.allanvital.devzao.domain.GitHubRepositorySyncStatus;
import com.allanvital.devzao.domain.GitHubUser;
import com.allanvital.devzao.domain.GitHubUserRepository;
import com.allanvital.devzao.github.GitHubApiCallCategory;

import com.allanvital.devzao.github.dto.GitHubRepositoryInfo;
import jakarta.jms.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Service
public class GitHubRepositorySyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositorySyncService.class);
    private static final Duration RATE_LIMIT_FALLBACK_DELAY = Duration.ofSeconds(60);

    private final GitHubUserRepository gitHubUserRepository;
    private final GitHubRepositoryRepository gitHubRepositoryRepository;
    private final JmsTemplate jmsTemplate;

    public GitHubRepositorySyncService(
            GitHubUserRepository gitHubUserRepository,
            GitHubRepositoryRepository gitHubRepositoryRepository,
            JmsTemplate jmsTemplate
    ) {
        this.gitHubUserRepository = gitHubUserRepository;
        this.gitHubRepositoryRepository = gitHubRepositoryRepository;
        this.jmsTemplate = jmsTemplate;
    }

    public String startSync(GitHubUser user) {
        return startSync(user, GitHubApiCallCategory.LIVE);
    }

    public String startSync(GitHubUser user, GitHubApiCallCategory category) {
        String syncToken = UUID.randomUUID().toString();
        user.setRepositorySyncStatus(GitHubRepositorySyncStatus.FETCHING);
        user.setRepositorySyncToken(syncToken);
        user.setRepositorySyncStartedAt(Instant.now());
        user.setRepositorySyncedAt(null);
        GitHubUser saved = this.gitHubUserRepository.save(user);

        enqueuePage(saved.getId(), 1, syncToken, category);
        return syncToken;
    }

    public Optional<String> startSync(Long gitHubUserId) {
        return startSync(gitHubUserId, GitHubApiCallCategory.LIVE);
    }

    public Optional<String> startSync(Long gitHubUserId, GitHubApiCallCategory category) {
        Optional<GitHubUser> userOpt = this.gitHubUserRepository.findById(gitHubUserId);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(startSync(userOpt.get(), category));
    }

    public void enqueuePage(Long gitHubUserId, int page, String syncToken) {
        enqueuePage(gitHubUserId, page, syncToken, GitHubApiCallCategory.LIVE);
    }

    public void enqueuePage(Long gitHubUserId, int page, String syncToken, GitHubApiCallCategory category) {
        GitHubRepositorySyncMessage message = new GitHubRepositorySyncMessage(gitHubUserId, page, syncToken, category);
        log.info("Enqueuing repository sync for user id={} page={} category={}", gitHubUserId, page, category);
        this.jmsTemplate.convertAndSend(Constants.USER_REPOSITORY_SYNC_QUEUE, message);
    }

    public void reschedulePage(Long gitHubUserId, int page, String syncToken, Instant when) {
        reschedulePage(gitHubUserId, page, syncToken, when, GitHubApiCallCategory.LIVE);
    }

    public void reschedulePage(Long gitHubUserId, int page, String syncToken, Instant when, GitHubApiCallCategory category) {
        long deliveryTimeMillis = ArtemisScheduling.toDeliveryTimeMillis(when, RATE_LIMIT_FALLBACK_DELAY);
        GitHubRepositorySyncMessage message = new GitHubRepositorySyncMessage(gitHubUserId, page, syncToken, category);
        log.info("Rescheduling repository sync for user id={} page={} at {}", gitHubUserId, page, Instant.ofEpochMilli(deliveryTimeMillis));
        this.jmsTemplate.convertAndSend(Constants.USER_REPOSITORY_SYNC_QUEUE, message, (Message jmsMessage) -> {
            jmsMessage.setLongProperty(ArtemisScheduling.SCHEDULED_DELIVERY, deliveryTimeMillis);
            return jmsMessage;
        });
    }

    @Transactional
    public void markFailed(Long gitHubUserId, String syncToken) {
        Optional<GitHubUser> userOpt = this.gitHubUserRepository.findById(gitHubUserId);
        if (userOpt.isEmpty()) {
            return;
        }

        GitHubUser user = userOpt.get();
        if (!syncToken.equals(user.getRepositorySyncToken())) {
            return;
        }

        user.setRepositorySyncStatus(GitHubRepositorySyncStatus.FAILED);
        this.gitHubUserRepository.save(user);
    }

    @Transactional
    public void savePage(GitHubUser user, List<GitHubRepositoryInfo> repositories, String syncToken) {
        for (GitHubRepositoryInfo repositoryInfo : repositories) {
            if (repositoryInfo.fork()) {
                continue;
            }

            GitHubRepository repository = this.gitHubRepositoryRepository.findByGithubRepoId(repositoryInfo.githubRepoId())
                    .orElseGet(() -> new GitHubRepository(user, repositoryInfo.githubRepoId()));
            repository.setName(repositoryInfo.name());
            repository.setFullName(repositoryInfo.fullName());
            repository.setDescription(repositoryInfo.description());
            repository.setHtmlUrl(repositoryInfo.htmlUrl().toString());
            repository.setCloneUrl(repositoryInfo.cloneUrl().toString());
            repository.setStargazersCount(repositoryInfo.stargazersCount());
            repository.setForksCount(repositoryInfo.forksCount());
            repository.setWatchersCount(repositoryInfo.watchersCount());
            repository.setLanguage(repositoryInfo.language());
            repository.setArchived(repositoryInfo.archived());
            repository.setCreatedAt(repositoryInfo.createdAt());
            repository.setUpdatedAt(repositoryInfo.updatedAt());
            repository.setPushedAt(repositoryInfo.pushedAt());
            repository.setIsPrivate(repositoryInfo.isPrivate());
            repository.setLastSeenSyncToken(syncToken);
            this.gitHubRepositoryRepository.save(repository);
        }
    }

    @Transactional
    public void finalizeSync(Long gitHubUserId, String syncToken) {
        Optional<GitHubUser> userOpt = this.gitHubUserRepository.findById(gitHubUserId);
        if (userOpt.isEmpty()) {
            return;
        }

        GitHubUser user = userOpt.get();
        if (!syncToken.equals(user.getRepositorySyncToken())) {
            return;
        }

        int deleted = this.gitHubRepositoryRepository.deleteMissingFromSync(gitHubUserId, syncToken);
        log.info("Repository sync finished for user id={}, deleted {} missing repositories", gitHubUserId, deleted);

        user.setRepositorySyncStatus(GitHubRepositorySyncStatus.FETCHED);
        user.setRepositorySyncedAt(Instant.now());
        user.setDeepAnalysisStatus(null);
        this.gitHubUserRepository.save(user);
    }
}
