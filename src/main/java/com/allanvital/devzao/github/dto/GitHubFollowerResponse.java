package com.allanvital.devzao.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record GitHubFollowerResponse(
        Long id,
        String login,
        @JsonProperty("avatar_url") String avatarUrl
) {

}
