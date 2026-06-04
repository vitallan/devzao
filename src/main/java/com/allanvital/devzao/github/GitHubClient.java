package com.allanvital.devzao.github;

import com.allanvital.devzao.github.dto.GitHubAvatar;
import com.allanvital.devzao.github.dto.GitHubFollower;
import com.allanvital.devzao.github.dto.GitHubRepositoryInfo;
import com.allanvital.devzao.github.dto.GitHubUserInfo;

import java.net.URI;
import java.util.List;

/**
* @author Allan Vital (https://allanvital.com)
  */
public interface GitHubClient {

    GitHubUserInfo fetchUser(String login) throws GitHubClientException;

    GitHubAvatar fetchAvatar(URI avatarUrl) throws GitHubClientException;

    List<GitHubRepositoryInfo> fetchRepositories(String login, int page, int perPage) throws GitHubClientException;

    List<GitHubFollower> fetchFollowers(String login, int page, int perPage) throws GitHubClientException;

}
