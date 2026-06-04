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
public class UserFollowerPropagationListener {

    private static final Logger log = LoggerFactory.getLogger(UserFollowerPropagationListener.class);

    private final UserFollowerPropagationService userFollowerPropagationService;

    public UserFollowerPropagationListener(UserFollowerPropagationService userFollowerPropagationService) {
        this.userFollowerPropagationService = userFollowerPropagationService;
    }

    @JmsListener(destination = Constants.USER_FOLLOWER_PROPAGATION_QUEUE, concurrency = "1")
    public void onMessage(UserFollowerPropagationMessage message) {
        Long userId = message.gitHubUserId();
        log.info("Consuming follower propagation message for user id={}", userId);
        try {
            this.userFollowerPropagationService.propagate(userId);
        } catch (Exception e) {
            log.error("Follower propagation failed for user id={}", userId, e);
        }
    }

}
