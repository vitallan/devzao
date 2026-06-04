package com.allanvital.devzao.github.dto;

import java.net.URI;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record GitHubUserInfo(
        long githubId,
        String login,
        String name,
        String bio,
        int followers,
        URI avatarUrl
) {
}
