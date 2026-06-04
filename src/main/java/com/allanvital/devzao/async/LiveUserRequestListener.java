package com.allanvital.devzao.async;

import com.allanvital.devzao.Constants;
import com.allanvital.devzao.domain.GitHubUser;
import com.allanvital.devzao.domain.GitHubUserPhoto;
import com.allanvital.devzao.domain.GitHubUserPhotoRepository;
import com.allanvital.devzao.domain.GitHubUserRepository;
import com.allanvital.devzao.domain.GitHubUserStatus;
import com.allanvital.devzao.github.dto.GitHubAvatar;
import com.allanvital.devzao.github.GitHubApiCallCategory;
import com.allanvital.devzao.github.GitHubClient;
import com.allanvital.devzao.github.GitHubClientException;
import com.allanvital.devzao.github.GitHubClientFailure;
import com.allanvital.devzao.github.GitHubRateLimitService;
import com.allanvital.devzao.github.dto.GitHubUserInfo;
import jakarta.jms.Message;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Component
public class LiveUserRequestListener {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private static final Duration RATE_LIMIT_FALLBACK_DELAY = Duration.ofSeconds(60);

    private final GitHubUserRepository gitHubUserRepository;
    private final GitHubUserPhotoRepository gitHubUserPhotoRepository;
    private final GitHubClient gitHubClient;
    private final JmsTemplate jmsTemplate;
    private final GitHubRepositorySyncService gitHubRepositorySyncService;
    private final GitHubRateLimitService gitHubRateLimitService;

    public LiveUserRequestListener(
            GitHubUserRepository gitHubUserRepository,
            GitHubUserPhotoRepository gitHubUserPhotoRepository,
            GitHubClient gitHubClient,
            JmsTemplate jmsTemplate,
            GitHubRepositorySyncService gitHubRepositorySyncService,
            GitHubRateLimitService gitHubRateLimitService
    ) {
        this.gitHubUserRepository = gitHubUserRepository;
        this.gitHubUserPhotoRepository = gitHubUserPhotoRepository;
        this.gitHubClient = gitHubClient;
        this.jmsTemplate = jmsTemplate;
        this.gitHubRepositorySyncService = gitHubRepositorySyncService;
        this.gitHubRateLimitService = gitHubRateLimitService;
    }

    @JmsListener(destination = Constants.USER_LIVE_REQUEST_QUEUE)
    public void onMessage(LiveUserRequestMessage message) {
        Long gitHubUserId = message.gitHubUserId();
        GitHubApiCallCategory category = message.category();
        log.info("Consuming live fetch message for user id={} category={}", gitHubUserId, category);
        Optional<GitHubUser> userOpt = this.gitHubUserRepository.findById(gitHubUserId);
        if (userOpt.isEmpty()) {
            log.info("Ignoring live fetch message for missing user id={}", gitHubUserId);
            return;
        }

        GitHubUser user = userOpt.get();
        if (user.getStatus() == GitHubUserStatus.FETCHED) {
            return;
        }

        try {
            if (!this.gitHubRateLimitService.tryAcquire(category)) {
                Instant retryAfter = this.gitHubRateLimitService.getStatus().resetAt();
                log.info("Live request rate-limited for user id={}, rescheduling until {}", user.getId(), retryAfter);
                this.reschedule(gitHubUserId, category, retryAfter);
                return;
            }

            GitHubUserInfo info = this.gitHubClient.fetchUser(user.getLogin());
            GitHubAvatar avatar = this.gitHubClient.fetchAvatar(info.avatarUrl());

            this.persistFetchedUser(user, info, avatar, category);
        } catch (GitHubClientException ex) {
            if (ex.getFailure() == GitHubClientFailure.RATE_LIMITED) {
                log.info("Rate limited while fetching user id={} login={}", user.getId(), user.getLogin());
                Instant when = ex.getRetryAfterEpochMillis() == null
                        ? Instant.now().plus(RATE_LIMIT_FALLBACK_DELAY)
                        : Instant.ofEpochMilli(ex.getRetryAfterEpochMillis());
                this.reschedule(gitHubUserId, category, when);
                return;
            }

            if (ex.getFailure() == GitHubClientFailure.NOT_FOUND) {
                this.markStatus(user.getId(), GitHubUserStatus.NOT_FOUND);
                return;
            }

            log.info("GitHub fetch failed for user id={} login={}: {}", user.getId(), user.getLogin(), ex.getMessage());
            this.markStatus(user.getId(), GitHubUserStatus.FAILED);
        } catch (Exception ex) {
            log.info("Unexpected fetch failure for user id={} login={}", user.getId(), user.getLogin(), ex);
            this.markStatus(user.getId(), GitHubUserStatus.FAILED);
        }
    }

    @Transactional
    public void persistFetchedUser(GitHubUser user, GitHubUserInfo info, GitHubAvatar avatar, GitHubApiCallCategory category) {
        String canonicalLogin = info.login();
        user.setGithubId(info.githubId());
        user.setLogin(canonicalLogin);
        user.setLoginLower(canonicalLogin.toLowerCase(Locale.ROOT));
        user.setName(info.name());
        user.setBio(info.bio());
        user.setFollowers(info.followers());
        user.setFetchedAt(Instant.now());
        user.setStatus(GitHubUserStatus.FETCHED);
        this.gitHubUserRepository.save(user);

        Optional<GitHubUserPhoto> existing = this.gitHubUserPhotoRepository.findByGitHubUserId(user.getId());
        if (existing.isPresent()) {
            GitHubUserPhoto photo = existing.get();
            photo.setPhotoBlob(avatar.bytes());
            photo.setContentType(avatar.contentType());
            this.gitHubUserPhotoRepository.save(photo);
        } else {
            this.gitHubUserPhotoRepository.save(new GitHubUserPhoto(user, avatar.bytes(), avatar.contentType()));
        }

        this.gitHubRepositorySyncService.startSync(user, category);
    }

    @Transactional
    public void markStatus(Long userId, GitHubUserStatus status) {
        Optional<GitHubUser> userOpt = this.gitHubUserRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return;
        }

        GitHubUser user = userOpt.get();
        user.setStatus(status);
        this.gitHubUserRepository.save(user);
    }

    private void reschedule(Long gitHubUserId, GitHubApiCallCategory category, Instant when) {
        long deliveryTimeMillis = ArtemisScheduling.toDeliveryTimeMillis(when, RATE_LIMIT_FALLBACK_DELAY);
        log.info("Rate limited, rescheduling live fetch for user id={} at {}", gitHubUserId, Instant.ofEpochMilli(deliveryTimeMillis));

        LiveUserRequestMessage message = new LiveUserRequestMessage(gitHubUserId, category);
        this.jmsTemplate.convertAndSend(Constants.USER_LIVE_REQUEST_QUEUE, message, (Message jmsMessage) -> {
            jmsMessage.setLongProperty(ArtemisScheduling.SCHEDULED_DELIVERY, deliveryTimeMillis);
            return jmsMessage;
        });
    }

}
