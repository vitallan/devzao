package com.allanvital.devzao;

/**
* @author Allan Vital (https://allanvital.com)
  */
public interface Constants {

    String USER_LIVE_REQUEST_QUEUE = "user.live.request";
    String USER_REPOSITORY_SYNC_QUEUE = "user.repository.sync";
    String USER_DEEP_ANALYSIS_QUEUE = "user.deep.analysis";
    String USER_DEEP_ANALYSIS_LIVE_QUEUE = "user.deep.analysis.live";
    String USER_DEEP_ANALYSIS_BACKGROUND_QUEUE = "user.deep.analysis.background";
    String USER_FOLLOWER_PROPAGATION_QUEUE = "user.follower.propagation";
    int GITHUB_REPOSITORIES_PER_PAGE = 100;

    String MESSAGE_PARAM = "message";
    String LOGIN_PARAM = "login";

}
