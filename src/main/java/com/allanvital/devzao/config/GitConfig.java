package com.allanvital.devzao.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Component
public class GitConfig {

    private final int cloneDepth;
    private final String cloneTempPath;
    private final int followerMaxPerUser;
    private final boolean followerPropagationEnabled;

    public GitConfig(
            @Value("${devzao.git.clone.depth:800}") int cloneDepth,
            @Value("${devzao.git.clone.temp-path:/tmp/devzao-git}") String cloneTempPath,
            @Value("${devzao.follower-analysis.max-per-user:50}") int followerMaxPerUser,
            @Value("${devzao.follower-analysis.enabled:true}") boolean followerPropagationEnabled
    ) {
        this.cloneDepth = cloneDepth;
        this.cloneTempPath = cloneTempPath;
        this.followerMaxPerUser = followerMaxPerUser;
        this.followerPropagationEnabled = followerPropagationEnabled;
    }

    public int getCloneDepth() {
        return this.cloneDepth;
    }

    public String getCloneTempPath() {
        return this.cloneTempPath;
    }

    public int getFollowerMaxPerUser() {
        return this.followerMaxPerUser;
    }

    public boolean isFollowerPropagationEnabled() {
        return this.followerPropagationEnabled;
    }
}
