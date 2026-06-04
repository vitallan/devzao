package com.allanvital.devzao.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record GitHubUserResponse(
        Long id,
        String login,
        String name,
        String bio,
        Integer followers,
        @JsonProperty("avatar_url") URI avatarUrl
) {
}
