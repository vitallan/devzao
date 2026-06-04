package com.allanvital.devzao.web;

import com.allanvital.devzao.domain.GitHubUserStatus;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record UserStatusResponse(
        GitHubUserStatus state,
        String message,
        String canonicalLogin,
        String attemptedLogin
) {
}
