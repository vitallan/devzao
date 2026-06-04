package com.allanvital.devzao.async;

import com.allanvital.devzao.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Component
public class GitDeepAnalysisBackgroundListener {

    private static final Logger log = LoggerFactory.getLogger(GitDeepAnalysisBackgroundListener.class);

    private final GitDeepAnalysisService gitDeepAnalysisService;

    public GitDeepAnalysisBackgroundListener(GitDeepAnalysisService gitDeepAnalysisService) {
        this.gitDeepAnalysisService = gitDeepAnalysisService;
    }

    @JmsListener(destination = Constants.USER_DEEP_ANALYSIS_BACKGROUND_QUEUE, concurrency = "1")
    public void onDeepAnalysisMessage(GitDeepAnalysisMessage message) {
        Long userId = message.gitHubUserId();
        log.info("Consuming background deep analysis message for user id={}", userId);
        try {
            this.gitDeepAnalysisService.analyze(userId);
        } catch (Exception e) {
            log.error("Background deep analysis failed for user id={}", userId, e);
            this.gitDeepAnalysisService.markFailed(userId);
        }
    }

}
