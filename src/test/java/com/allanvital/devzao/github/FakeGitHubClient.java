package com.allanvital.devzao.github;

import com.allanvital.devzao.github.dto.GitHubAvatar;
import com.allanvital.devzao.github.dto.GitHubFollower;
import com.allanvital.devzao.github.dto.GitHubRepositoryInfo;
import com.allanvital.devzao.github.dto.GitHubUserInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
* @author Allan Vital (https://allanvital.com)
  */
public class FakeGitHubClient implements GitHubClient {

    private final Map<String, UserOutcome> outcomesByLoginLower;
    private final Map<URI, AvatarOutcome> avatarOutcomesByUri;
    private final Map<RepositoryPageKey, RepositoryPageOutcome> repositoryOutcomesByPage;
    private final Map<FollowerPageKey, FollowerPageOutcome> followerOutcomesByPage;

    private static final String SUCCESS_LOGIN = "vitallan";
    private static final URI SUCCESS_AVATAR_URL = URI.create("https://example.com/avatar.png");
    public static final GitHubUserInfo SUCCESS_INFO = new GitHubUserInfo(123L, SUCCESS_LOGIN, "Allan", "cool bio", 7, SUCCESS_AVATAR_URL);

    public FakeGitHubClient() {
        this.outcomesByLoginLower = new ConcurrentHashMap<>();
        this.avatarOutcomesByUri = new ConcurrentHashMap<>();
        this.repositoryOutcomesByPage = new ConcurrentHashMap<>();
        this.followerOutcomesByPage = new ConcurrentHashMap<>();

        this.setUserSuccess(SUCCESS_LOGIN, SUCCESS_INFO);
        this.setAvatarSuccess(SUCCESS_AVATAR_URL, new GitHubAvatar("png".getBytes(UTF_8), "image/png"));
    }

    public void setUserSuccess(String login, GitHubUserInfo userInfo) {
        this.outcomesByLoginLower.put(toLower(login), UserOutcome.success(userInfo));
    }

    public void setUserFailure(String login, GitHubClientFailure failure) {
        this.outcomesByLoginLower.put(toLower(login), UserOutcome.failure(new GitHubClientException(failure, "forced")));
    }

    public void setUserRateLimited(String login, long retryAfterEpochMillis) {
        this.outcomesByLoginLower.put(
                toLower(login),
                UserOutcome.failure(new GitHubClientException(GitHubClientFailure.RATE_LIMITED, "forced", null, retryAfterEpochMillis))
        );
    }

    public void setAvatarSuccess(URI avatarUrl, GitHubAvatar avatar) {
        this.avatarOutcomesByUri.put(avatarUrl, AvatarOutcome.success(avatar));
    }

    public void setAvatarFailure(URI avatarUrl, GitHubClientFailure failure) {
        this.avatarOutcomesByUri.put(avatarUrl, AvatarOutcome.failure(new GitHubClientException(failure, "forced")));
    }

    public void setRepositoryPage(String login, int page, List<GitHubRepositoryInfo> repositories) {
        this.repositoryOutcomesByPage.put(
                new RepositoryPageKey(toLower(login), page),
                RepositoryPageOutcome.success(repositories)
        );
    }

    public void setRepositoryFailure(String login, int page, GitHubClientFailure failure) {
        this.repositoryOutcomesByPage.put(
                new RepositoryPageKey(toLower(login), page),
                RepositoryPageOutcome.failure(new GitHubClientException(failure, "forced"))
        );
    }

    public void setRepositoryRateLimited(String login, int page, long retryAfterEpochMillis) {
        this.repositoryOutcomesByPage.put(
                new RepositoryPageKey(toLower(login), page),
                RepositoryPageOutcome.failure(new GitHubClientException(GitHubClientFailure.RATE_LIMITED, "forced", null, retryAfterEpochMillis))
        );
    }

    public void setFollowerPage(String login, int page, List<GitHubFollower> followers) {
        this.followerOutcomesByPage.put(
                new FollowerPageKey(toLower(login), page),
                FollowerPageOutcome.success(followers)
        );
    }

    @Override
    public GitHubUserInfo fetchUser(String login) throws GitHubClientException {
        UserOutcome outcome = this.outcomesByLoginLower.get(toLower(login));
        if (outcome == null) {
            throw new GitHubClientException(GitHubClientFailure.UNAVAILABLE, "No outcome configured for login=" + login);
        }

        if (outcome.exception() != null) {
            throw outcome.exception();
        }

        return outcome.userInfo();
    }

    @Override
    public GitHubAvatar fetchAvatar(URI avatarUrl) throws GitHubClientException {
        AvatarOutcome outcome = this.avatarOutcomesByUri.get(avatarUrl);
        if (outcome == null) {
            throw new GitHubClientException(GitHubClientFailure.UNAVAILABLE, "No avatar outcome configured for url=" + avatarUrl);
        }

        if (outcome.exception() != null) {
            throw outcome.exception();
        }

        return outcome.avatar();
    }

    @Override
    public List<GitHubRepositoryInfo> fetchRepositories(String login, int page, int perPage) throws GitHubClientException {
        RepositoryPageOutcome outcome = this.repositoryOutcomesByPage.get(new RepositoryPageKey(toLower(login), page));
        if (outcome == null) {
            return List.of();
        }

        if (outcome.exception() != null) {
            throw outcome.exception();
        }

        return new ArrayList<>(outcome.repositories());
    }

    @Override
    public List<GitHubFollower> fetchFollowers(String login, int page, int perPage) throws GitHubClientException {
        FollowerPageOutcome outcome = this.followerOutcomesByPage.get(new FollowerPageKey(toLower(login), page));
        if (outcome == null) {
            return List.of();
        }

        if (outcome.exception() != null) {
            throw outcome.exception();
        }

        return new ArrayList<>(outcome.followers());
    }

    private static String toLower(String login) {
        if (login == null) {
            return "";
        }
        return login.trim().toLowerCase(Locale.ROOT);
    }

    private record UserOutcome(GitHubUserInfo userInfo, GitHubClientException exception) {

        public static UserOutcome success(GitHubUserInfo info) {
            return new UserOutcome(info, null);
        }

        public static UserOutcome failure(GitHubClientException ex) {
            return new UserOutcome(null, ex);
        }
    }

    private record AvatarOutcome(GitHubAvatar avatar, GitHubClientException exception) {

        public static AvatarOutcome success(GitHubAvatar avatar) {
            return new AvatarOutcome(avatar, null);
        }

        public static AvatarOutcome failure(GitHubClientException ex) {
            return new AvatarOutcome(null, ex);
        }
    }

    private record RepositoryPageKey(String loginLower, int page) {
    }

    private record RepositoryPageOutcome(List<GitHubRepositoryInfo> repositories, GitHubClientException exception) {

        public static RepositoryPageOutcome success(List<GitHubRepositoryInfo> repositories) {
            return new RepositoryPageOutcome(new ArrayList<>(repositories), null);
        }

        public static RepositoryPageOutcome failure(GitHubClientException ex) {
            return new RepositoryPageOutcome(List.of(), ex);
        }
    }

    private record FollowerPageKey(String loginLower, int page) {
    }

    private record FollowerPageOutcome(List<GitHubFollower> followers, GitHubClientException exception) {

        public static FollowerPageOutcome success(List<GitHubFollower> followers) {
            return new FollowerPageOutcome(new ArrayList<>(followers), null);
        }

        public static FollowerPageOutcome failure(GitHubClientException ex) {
            return new FollowerPageOutcome(List.of(), ex);
        }
    }

}
