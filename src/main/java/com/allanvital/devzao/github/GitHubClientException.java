package com.allanvital.devzao.github;

/**
* @author Allan Vital (https://allanvital.com)
  */
public class GitHubClientException extends Exception {

    private final GitHubClientFailure failure;
    private final Long retryAfterEpochMillis;

    public GitHubClientException(GitHubClientFailure failure, String message) {
        this(failure, message, null, null);
    }

    public GitHubClientException(GitHubClientFailure failure, String message, Throwable cause) {
        this(failure, message, cause, null);
    }

    public GitHubClientException(GitHubClientFailure failure, String message, Throwable cause, Long retryAfterEpochMillis) {
        super(message, cause);
        this.failure = failure;
        this.retryAfterEpochMillis = retryAfterEpochMillis;
    }

    public GitHubClientFailure getFailure() {
        return this.failure;
    }

    public Long getRetryAfterEpochMillis() {
        return this.retryAfterEpochMillis;
    }
}
