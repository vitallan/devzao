package com.allanvital.devzao.async;

import com.allanvital.devzao.Constants;
import com.allanvital.devzao.config.GitConfig;
import com.allanvital.devzao.domain.GitHubUser;
import com.allanvital.devzao.domain.GitHubUserRepository;
import com.allanvital.devzao.domain.GitHubUserStatus;
import com.allanvital.devzao.github.GitHubApiCallCategory;
import com.allanvital.devzao.github.GitHubClient;
import com.allanvital.devzao.github.GitHubClientException;
import com.allanvital.devzao.github.GitHubClientFailure;
import com.allanvital.devzao.github.GitHubRateLimitService;
import com.allanvital.devzao.github.dto.GitHubFollower;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Service
public class UserFollowerPropagationService {

    private static final Logger log = LoggerFactory.getLogger(UserFollowerPropagationService.class);
    private static final Duration RATE_LIMIT_FALLBACK_DELAY = Duration.ofSeconds(60);
    private static final int GITHUB_FOLLOWERS_PER_PAGE = 100;
    private static final Random RANDOM = new Random();

    private final GitHubUserRepository gitHubUserRepository;
    private final GitHubClient gitHubClient;
    private final GitHubRateLimitService gitHubRateLimitService;
    private final JmsTemplate jmsTemplate;
    private final GitConfig gitConfig;

    public UserFollowerPropagationService(
            GitHubUserRepository gitHubUserRepository,
            GitHubClient gitHubClient,
            GitHubRateLimitService gitHubRateLimitService,
            JmsTemplate jmsTemplate,
            GitConfig gitConfig
    ) {
        this.gitHubUserRepository = gitHubUserRepository;
        this.gitHubClient = gitHubClient;
        this.gitHubRateLimitService = gitHubRateLimitService;
        this.jmsTemplate = jmsTemplate;
        this.gitConfig = gitConfig;
    }

    public void propagate(Long gitHubUserId) {
        if (!this.gitConfig.isFollowerPropagationEnabled()) {
            log.info("Follower propagation is disabled, skipping for user id={}", gitHubUserId);
            return;
        }

        Optional<GitHubUser> userOpt = this.gitHubUserRepository.findById(gitHubUserId);
        if (userOpt.isEmpty()) {
            log.warn("Cannot propagate followers for missing user id={}", gitHubUserId);
            return;
        }

        GitHubUser user = userOpt.get();
        int maxFollowers = this.gitConfig.getFollowerMaxPerUser();
        log.info("Propagating followers for user id={} login={}, max={}", gitHubUserId, user.getLogin(), maxFollowers);

        List<GitHubFollower> allFollowers;
        try {
            if (!this.gitHubRateLimitService.tryAcquire(GitHubApiCallCategory.BACKGROUND)) {
                log.info("Rate limited for follower propagation, will retry later for user id={}", gitHubUserId);
                return;
            }
            allFollowers = this.gitHubClient.fetchFollowers(user.getLogin(), 1, GITHUB_FOLLOWERS_PER_PAGE);
        } catch (GitHubClientException ex) {
            if (ex.getFailure() == GitHubClientFailure.RATE_LIMITED) {
                log.info("Rate limited while fetching followers for user id={}", gitHubUserId);
            } else {
                log.warn("Failed to fetch followers for user id={}: {}", gitHubUserId, ex.getMessage());
            }
            return;
        }

        long alreadyKnown = allFollowers.stream()
                .filter(f -> this.gitHubUserRepository.findByLoginLower(f.login().toLowerCase(Locale.ROOT)).isPresent())
                .count();
        List<GitHubFollower> newFollowers = allFollowers.stream()
                .filter(f -> this.gitHubUserRepository.findByLoginLower(f.login().toLowerCase(Locale.ROOT)).isEmpty())
                .toList();

        log.info("Found {} followers for user id={}, {} already known, {} new",
                allFollowers.size(), gitHubUserId, alreadyKnown, newFollowers.size());

        int sampleSize = Math.min(maxFollowers, newFollowers.size());
        if (sampleSize == 0) {
            log.info("No new followers to propagate for user id={}", gitHubUserId);
            return;
        }

        List<GitHubFollower> sampled = RANDOM.ints(0, newFollowers.size())
                .distinct()
                .limit(sampleSize)
                .mapToObj(newFollowers::get)
                .toList();

        for (GitHubFollower follower : sampled) {
            String loginLower = follower.login().toLowerCase(Locale.ROOT);
            GitHubUser created = this.gitHubUserRepository.save(
                    new GitHubUser(follower.login(), loginLower, GitHubUserStatus.NEW)
            );
            log.info("Propagated follower login={} (id={}) from user id={}",
                    follower.login(), created.getId(), gitHubUserId);
            LiveUserRequestMessage message = new LiveUserRequestMessage(created.getId(), GitHubApiCallCategory.BACKGROUND);
            this.jmsTemplate.convertAndSend(Constants.USER_LIVE_REQUEST_QUEUE, message);
        }

        log.info("Follower propagation completed for user id={}: enqueued {} followers", gitHubUserId, sampled.size());
    }

}
