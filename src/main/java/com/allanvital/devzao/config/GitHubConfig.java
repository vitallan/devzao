package com.allanvital.devzao.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Component
public class GitHubConfig {

    private final String token;

    public GitHubConfig(@Value("${github.token:}") String token) {
        this.token = token;
    }

    public String getToken() {
        return this.token;
    }
}
