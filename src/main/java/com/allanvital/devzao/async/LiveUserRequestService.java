package com.allanvital.devzao.async;

import com.allanvital.devzao.Constants;
import com.allanvital.devzao.domain.GitHubUser;
import com.allanvital.devzao.domain.GitHubUserRepository;
import com.allanvital.devzao.domain.GitHubUserStatus;
import com.allanvital.devzao.github.GitHubApiCallCategory;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Service
public class LiveUserRequestService {

    private static final Logger log = LoggerFactory.getLogger(LiveUserRequestService.class);

    private final GitHubUserRepository gitHubUserRepository;
    private final JmsTemplate jmsTemplate;

    public LiveUserRequestService(GitHubUserRepository gitHubUserRepository, JmsTemplate jmsTemplate) {
        this.gitHubUserRepository = gitHubUserRepository;
        this.jmsTemplate = jmsTemplate;
    }

    public void enqueueRefresh(Long gitHubUserId) {
        enqueueRefresh(gitHubUserId, GitHubApiCallCategory.LIVE);
    }

    public void enqueueRefresh(Long gitHubUserId, GitHubApiCallCategory category) {
        Optional<GitHubUser> existing = this.gitHubUserRepository.findById(gitHubUserId);
        if (existing.isEmpty()) {
            log.warn("Cannot enqueue refresh for missing user id={}", gitHubUserId);
            return;
        }
        GitHubUser user = existing.get();
        user.setStatus(GitHubUserStatus.STALE);
        user.setDeepAnalysisStatus(null);
        this.gitHubUserRepository.save(user);
        log.info("Enqueuing refresh for login={} (id={}) category={}", user.getLogin(), user.getId(), category);
        LiveUserRequestMessage message = new LiveUserRequestMessage(user.getId(), category);
        this.jmsTemplate.convertAndSend(Constants.USER_LIVE_REQUEST_QUEUE, message);
    }

    public GitHubUser ensureUserAndEnqueue(String rawLogin) {
        String login = rawLogin == null ? "" : rawLogin.trim();
        String loginLower = login.toLowerCase(Locale.ROOT);

        Optional<GitHubUser> existing = this.gitHubUserRepository.findByLoginLower(loginLower);
        if (existing.isPresent()) {
            GitHubUser user = existing.get();
            if (user.getStatus() == GitHubUserStatus.NEW) {
                log.info("Re-enqueuing live fetch for login={} (id={})", login, user.getId());
                LiveUserRequestMessage message = new LiveUserRequestMessage(user.getId(), GitHubApiCallCategory.LIVE);
                this.jmsTemplate.convertAndSend(Constants.USER_LIVE_REQUEST_QUEUE, message);
            }
            return user;
        }

        GitHubUser created = this.gitHubUserRepository.save(new GitHubUser(login, loginLower, GitHubUserStatus.NEW));
        log.info("Enqueuing live fetch for login={} (id={})", login, created.getId());

        LiveUserRequestMessage message = new LiveUserRequestMessage(created.getId(), GitHubApiCallCategory.LIVE);
        this.jmsTemplate.convertAndSend(Constants.USER_LIVE_REQUEST_QUEUE, message);
        return created;
    }
}
