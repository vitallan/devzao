package com.allanvital.devzao.github.dto;

import java.net.URI;
import java.time.Instant;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record GitHubRepositoryInfo(
        long githubRepoId,
        String name,
        String fullName,
        String description,
        URI htmlUrl,
        URI cloneUrl,
        boolean fork,
        int stargazersCount,
        int forksCount,
        int watchersCount,
        String language,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        Instant pushedAt,
        boolean isPrivate
) {
}
