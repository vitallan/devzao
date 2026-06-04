package com.allanvital.devzao.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.time.Instant;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record GitHubRepositoryResponse(
        Long id,
        String name,
        @JsonProperty("full_name") String fullName,
        String description,
        @JsonProperty("html_url") URI htmlUrl,
        @JsonProperty("clone_url") URI cloneUrl,
        Boolean fork,
        @JsonProperty("stargazers_count") Integer stargazersCount,
        @JsonProperty("forks_count") Integer forksCount,
        @JsonProperty("watchers_count") Integer watchersCount,
        String language,
        Boolean archived,
        @JsonProperty("private") Boolean isPrivate,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("pushed_at") Instant pushedAt
) {
}
