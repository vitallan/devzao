package com.allanvital.devzao.github.remote;

import com.allanvital.devzao.config.GitHubConfig;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.allanvital.devzao.github.GitHubClient;
import com.allanvital.devzao.github.GitHubClientException;
import com.allanvital.devzao.github.GitHubClientFailure;
import com.allanvital.devzao.github.GitHubRateLimitService;
import com.allanvital.devzao.github.dto.GitHubAvatar;
import com.allanvital.devzao.github.dto.GitHubFollower;
import com.allanvital.devzao.github.dto.GitHubFollowerResponse;
import com.allanvital.devzao.github.dto.GitHubRepositoryInfo;
import com.allanvital.devzao.github.dto.GitHubRepositoryResponse;
import com.allanvital.devzao.github.dto.GitHubUserInfo;
import com.allanvital.devzao.github.dto.GitHubUserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Component
public class RestGitHubClient implements GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(RestGitHubClient.class);

    private final RestClient restClient;
    private final GitHubConfig gitHubConfig;
    private final GitHubRateLimitService gitHubRateLimitService;

    public RestGitHubClient(RestClient.Builder restClientBuilder, GitHubConfig gitHubConfig, GitHubRateLimitService gitHubRateLimitService) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
        this.gitHubConfig = gitHubConfig;
        this.gitHubRateLimitService = gitHubRateLimitService;
    }

    @Override
    public GitHubUserInfo fetchUser(String login) throws GitHubClientException {
        try {
            ResponseEntity<GitHubUserResponse> response = this.restClient.get()
                    .uri("/users/{login}", login)
                    .headers(this::applyAuth)
                    .retrieve()
                    .toEntity(GitHubUserResponse.class);

            recordRateLimit(response.getHeaders());

            GitHubUserResponse body = response.getBody();
            if (body == null) {
                throw new GitHubClientException(GitHubClientFailure.UNAVAILABLE, "GitHub returned empty body for user lookup");
            }
            if (body.id() == null || !StringUtils.hasText(body.login()) || body.avatarUrl() == null) {
                throw new GitHubClientException(GitHubClientFailure.UNAVAILABLE, "GitHub returned incomplete user response");
            }

            return new GitHubUserInfo(
                    body.id(),
                    body.login(),
                    body.name(),
                    body.bio(),
                    body.followers() == null ? 0 : body.followers(),
                    body.avatarUrl()
            );
        } catch (RestClientResponseException ex) {
            recordRateLimit(ex.getResponseHeaders());

            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new GitHubClientException(GitHubClientFailure.NOT_FOUND, "GitHub user not found");
            }
            if (ex.getStatusCode() == HttpStatus.FORBIDDEN && isRateLimited(ex.getResponseHeaders())) {
                Instant reset = readRateLimitResetInstant(ex.getResponseHeaders());
                throw new GitHubClientException(
                        GitHubClientFailure.RATE_LIMITED,
                        "Rate limited until " + reset,
                        ex,
                        reset == null ? null : reset.toEpochMilli()
                );
            }

            throw new GitHubClientException(
                    GitHubClientFailure.UNAVAILABLE,
                    "GitHub user lookup failed with status " + ex.getStatusCode(),
                    ex
            );
        } catch (Exception ex) {
            throw new GitHubClientException(GitHubClientFailure.UNAVAILABLE, "GitHub user lookup failed", ex);
        }
    }

    @Override
    public GitHubAvatar fetchAvatar(URI avatarUrl) throws GitHubClientException {
        try {
            ResponseEntity<byte[]> response = RestClient.builder()
                    .build()
                    .get()
                    .uri(avatarUrl)
                    .headers(this::applyAuth)
                    .accept(MediaType.ALL)
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] bytes = response.getBody();
            if (bytes == null || bytes.length == 0) {
                throw new GitHubClientException(GitHubClientFailure.UNAVAILABLE, "GitHub returned empty avatar");
            }

            MediaType contentType = response.getHeaders().getContentType();
            String contentTypeString = contentType == null ? null : contentType.toString();
            return new GitHubAvatar(bytes, contentTypeString);
        } catch (RestClientResponseException ex) {
            recordRateLimit(ex.getResponseHeaders());

            if (ex.getStatusCode() == HttpStatus.FORBIDDEN && isRateLimited(ex.getResponseHeaders())) {
                Instant reset = readRateLimitResetInstant(ex.getResponseHeaders());
                throw new GitHubClientException(
                        GitHubClientFailure.RATE_LIMITED,
                        "Rate limited until " + reset,
                        ex,
                        reset == null ? null : reset.toEpochMilli()
                );
            }
            throw new GitHubClientException(
                    GitHubClientFailure.UNAVAILABLE,
                    "GitHub avatar fetch failed with status " + ex.getStatusCode(),
                    ex
            );
        } catch (Exception ex) {
            throw new GitHubClientException(GitHubClientFailure.UNAVAILABLE, "GitHub avatar fetch failed", ex);
        }
    }

    @Override
    public List<GitHubRepositoryInfo> fetchRepositories(String login, int page, int perPage) throws GitHubClientException {
        try {
            ResponseEntity<GitHubRepositoryResponse[]> response = this.restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/users/{login}/repos")
                            .queryParam("type", "owner")
                            .queryParam("sort", "updated")
                            .queryParam("per_page", perPage)
                            .queryParam("page", page)
                            .build(login))
                    .headers(this::applyAuth)
                    .retrieve()
                    .toEntity(GitHubRepositoryResponse[].class);

            recordRateLimit(response.getHeaders());

            GitHubRepositoryResponse[] body = response.getBody();
            if (body == null) {
                throw new GitHubClientException(GitHubClientFailure.UNAVAILABLE, "GitHub returned empty body for repository lookup");
            }

            List<GitHubRepositoryInfo> repositories = new ArrayList<>();
            for (GitHubRepositoryResponse repository : Arrays.asList(body)) {
                repositories.add(toRepositoryInfo(repository));
            }
            return repositories;
        } catch (RestClientResponseException ex) {
            recordRateLimit(ex.getResponseHeaders());

            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new GitHubClientException(GitHubClientFailure.NOT_FOUND, "GitHub user repositories not found");
            }
            if (ex.getStatusCode() == HttpStatus.FORBIDDEN && isRateLimited(ex.getResponseHeaders())) {
                Instant reset = readRateLimitResetInstant(ex.getResponseHeaders());
                throw new GitHubClientException(
                        GitHubClientFailure.RATE_LIMITED,
                        "Rate limited until " + reset,
                        ex,
                        reset == null ? null : reset.toEpochMilli()
                );
            }

            throw new GitHubClientException(
                    GitHubClientFailure.UNAVAILABLE,
                    "GitHub repository lookup failed with status " + ex.getStatusCode(),
                    ex
            );
        } catch (GitHubClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GitHubClientException(GitHubClientFailure.UNAVAILABLE, "GitHub repository lookup failed", ex);
        }
    }

    @Override
    public List<GitHubFollower> fetchFollowers(String login, int page, int perPage) throws GitHubClientException {
        try {
            ResponseEntity<GitHubFollowerResponse[]> response = this.restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/users/{login}/followers")
                            .queryParam("per_page", perPage)
                            .queryParam("page", page)
                            .build(login))
                    .headers(this::applyAuth)
                    .retrieve()
                    .toEntity(GitHubFollowerResponse[].class);

            recordRateLimit(response.getHeaders());

            GitHubFollowerResponse[] body = response.getBody();
            if (body == null) {
                throw new GitHubClientException(GitHubClientFailure.UNAVAILABLE, "GitHub returned empty body for follower lookup");
            }

            List<GitHubFollower> followers = new ArrayList<>();
            for (GitHubFollowerResponse follower : Arrays.asList(body)) {
                if (follower.id() == null || follower.login() == null || follower.login().isBlank()) {
                    continue;
                }
                followers.add(new GitHubFollower(follower.id(), follower.login()));
            }
            return followers;
        } catch (RestClientResponseException ex) {
            recordRateLimit(ex.getResponseHeaders());

            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new GitHubClientException(GitHubClientFailure.NOT_FOUND, "GitHub user followers not found");
            }
            if (ex.getStatusCode() == HttpStatus.FORBIDDEN && isRateLimited(ex.getResponseHeaders())) {
                Instant reset = readRateLimitResetInstant(ex.getResponseHeaders());
                throw new GitHubClientException(
                        GitHubClientFailure.RATE_LIMITED,
                        "Rate limited until " + reset,
                        ex,
                        reset == null ? null : reset.toEpochMilli()
                );
            }

            throw new GitHubClientException(
                    GitHubClientFailure.UNAVAILABLE,
                    "GitHub follower lookup failed with status " + ex.getStatusCode(),
                    ex
            );
        } catch (GitHubClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GitHubClientException(GitHubClientFailure.UNAVAILABLE, "GitHub follower lookup failed", ex);
        }
    }

    private void recordRateLimit(HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        try {
            List<String> remainingValues = headers.get("X-RateLimit-Remaining");
            List<String> limitValues = headers.get("X-RateLimit-Limit");
            List<String> resetValues = headers.get("X-RateLimit-Reset");
            if (remainingValues != null && !remainingValues.isEmpty()
                    && limitValues != null && !limitValues.isEmpty()
                    && resetValues != null && !resetValues.isEmpty()) {
                int remaining = Integer.parseInt(remainingValues.getFirst());
                int limit = Integer.parseInt(limitValues.getFirst());
                long resetEpochSecond = Long.parseLong(resetValues.getFirst());
                this.gitHubRateLimitService.recordResponse(remaining, limit, resetEpochSecond);
            }
        } catch (Exception ex) {
            log.debug("Failed to parse rate limit headers", ex);
        }
    }

    private void applyAuth(HttpHeaders headers) {
        if (StringUtils.hasText(this.gitHubConfig.getToken())) {
            headers.setBearerAuth(this.gitHubConfig.getToken().trim());
        }
    }

    private boolean isRateLimited(HttpHeaders headers) {
        if (headers == null) {
            return false;
        }
        List<String> remaining = headers.get("X-RateLimit-Remaining");
        if (remaining == null || remaining.isEmpty()) {
            return false;
        }
        return "0".equals(remaining.getFirst());
    }

    private Instant readRateLimitResetInstant(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        List<String> reset = headers.get("X-RateLimit-Reset");
        if (reset == null || reset.isEmpty()) {
            return null;
        }
        try {
            long epochSeconds = Long.parseLong(reset.getFirst());
            return Instant.ofEpochSecond(epochSeconds);
        } catch (Exception ex) {
            log.debug("Could not parse X-RateLimit-Reset header: {}", reset);
            return null;
        }
    }

    private GitHubRepositoryInfo toRepositoryInfo(GitHubRepositoryResponse repository) throws GitHubClientException {
        if (repository.id() == null || !StringUtils.hasText(repository.name()) || !StringUtils.hasText(repository.fullName())
                || repository.htmlUrl() == null || repository.cloneUrl() == null) {
            throw new GitHubClientException(GitHubClientFailure.UNAVAILABLE, "GitHub returned incomplete repository response");
        }

        return new GitHubRepositoryInfo(
                repository.id(),
                repository.name(),
                repository.fullName(),
                repository.description(),
                repository.htmlUrl(),
                repository.cloneUrl(),
                Boolean.TRUE.equals(repository.fork()),
                repository.stargazersCount() == null ? 0 : repository.stargazersCount(),
                repository.forksCount() == null ? 0 : repository.forksCount(),
                repository.watchersCount() == null ? 0 : repository.watchersCount(),
                repository.language(),
                Boolean.TRUE.equals(repository.archived()),
                repository.createdAt(),
                repository.updatedAt(),
                repository.pushedAt(),
                Boolean.TRUE.equals(repository.isPrivate())
        );
    }
}
