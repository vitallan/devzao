package com.allanvital.devzao.async;

import com.allanvital.devzao.Constants;
import com.allanvital.devzao.domain.GitDeepAnalysisStatus;
import com.allanvital.devzao.domain.GitHubUser;
import com.allanvital.devzao.domain.GitHubUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Component
public class GitDeepAnalysisLiveListener {

    private static final Logger log = LoggerFactory.getLogger(GitDeepAnalysisLiveListener.class);

    private final GitDeepAnalysisService gitDeepAnalysisService;
    private final GitHubUserRepository gitHubUserRepository;
    private final JmsTemplate jmsTemplate;

    public GitDeepAnalysisLiveListener(
            GitDeepAnalysisService gitDeepAnalysisService,
            GitHubUserRepository gitHubUserRepository,
            JmsTemplate jmsTemplate
    ) {
        this.gitDeepAnalysisService = gitDeepAnalysisService;
        this.gitHubUserRepository = gitHubUserRepository;
        this.jmsTemplate = jmsTemplate;
    }

    @JmsListener(destination = Constants.USER_DEEP_ANALYSIS_LIVE_QUEUE, concurrency = "1")
    public void onDeepAnalysisMessage(GitDeepAnalysisMessage message) {
        Long userId = message.gitHubUserId();
        log.info("Consuming live deep analysis message for user id={}", userId);
        try {
            this.gitDeepAnalysisService.analyze(userId);
            Optional<GitHubUser> userOpt = this.gitHubUserRepository.findById(userId);
            if (userOpt.isPresent() && userOpt.get().getDeepAnalysisStatus() == GitDeepAnalysisStatus.COMPLETED) {
                UserFollowerPropagationMessage propagationMessage = new UserFollowerPropagationMessage(userId);
                this.jmsTemplate.convertAndSend(Constants.USER_FOLLOWER_PROPAGATION_QUEUE, propagationMessage);
            }
        } catch (Exception e) {
            log.error("Live deep analysis failed for user id={}", userId, e);
            this.gitDeepAnalysisService.markFailed(userId);
        }
    }

}
