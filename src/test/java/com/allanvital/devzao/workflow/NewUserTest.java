package com.allanvital.devzao.workflow;

import com.allanvital.devzao.domain.GitHubUser;
import com.allanvital.devzao.domain.GitHubUserPhoto;
import com.allanvital.devzao.domain.GitHubUserStatus;
import com.allanvital.devzao.github.GitHubClientFailure;
import com.allanvital.devzao.github.dto.GitHubAvatar;
import com.allanvital.devzao.github.dto.GitHubUserInfo;
import com.allanvital.devzao.web.HomeMessage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.allanvital.devzao.github.FakeGitHubClient.SUCCESS_INFO;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public class NewUserTest extends E2ETest {

    private static final Duration USER_TIMEOUT = Duration.ofSeconds(5);
    private static final GitHubAvatar PNG_AVATAR = new GitHubAvatar("png".getBytes(UTF_8), "image/png");

    private static final GitHubUserInfo CACHED_INFO = new GitHubUserInfo(
            124L,
            "cached-user",
            "Cached User",
            "already fetched",
            8,
            URI.create("https://example.com/cached-user.png")
    );
    private static final GitHubUserInfo CANONICAL_INFO = new GitHubUserInfo(
            125L,
            "CanonicalCase",
            "Canonical User",
            "canonical login",
            9,
            URI.create("https://example.com/canonical-case.png")
    );
    private static final GitHubUserInfo STATUS_INFO = new GitHubUserInfo(
            126L,
            "status-transition",
            "Status User",
            "status flow",
            10,
            URI.create("https://example.com/status-transition.png")
    );

    @Test
    public void shouldFetchUserAndPersistPhoto() throws Exception {
        String login = SUCCESS_INFO.login();

        String url = "/u/vitallan";

        this.mockMvc.perform(post("/search")
                .param("login", login))
                .andExpect(redirectedUrl(url))
                .andExpect(status().is(302));

        this.mockMvc.perform(get(url))
                .andExpect(model().attribute("login", login))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilFetched(login, USER_TIMEOUT);

        this.mockMvc.perform(get(url))
                .andExpect(model().attribute("login", login))
                .andExpect(model().attribute("name", SUCCESS_INFO.name()))
                .andExpect(model().attribute("bio", SUCCESS_INFO.bio()))
                .andExpect(model().attribute("followers", SUCCESS_INFO.followers()))
                .andExpect(model().attributeExists("fetchedAt"))
                .andExpect(model().attributeExists("photoDataUrl"))
                .andExpect(view().name("user"))
                .andExpect(status().isOk());

        GitHubUser user = this.gitHubUserRepository.findByLoginLower(login).orElseThrow();
        Optional<GitHubUserPhoto> photoOpt = this.gitHubUserPhotoRepository.findByGitHubUserId(user.getId());
        assertTrue(photoOpt.isPresent());
        assertArrayEquals("png".getBytes(UTF_8), photoOpt.get().getPhotoBlob());
        assertEquals("image/png", photoOpt.get().getContentType());
    }

    @Test
    public void shouldRedirectSearchToTrimmedLogin() throws Exception {
        this.mockMvc.perform(post("/search")
                .param("login", "  trimmed-login  "))
                .andExpect(redirectedUrl("/u/trimmed-login"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    public void shouldRedirectHomeWhenGitHubUserIsNotFound() throws Exception {
        String login = "missing-user";
        this.fakeGitHubClient.setUserFailure(login, GitHubClientFailure.NOT_FOUND);

        this.mockMvc.perform(get("/u/" + login))
                .andExpect(model().attribute("login", login))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilNotFound(login, USER_TIMEOUT);

        this.mockMvc.perform(get("/u/" + login))
                .andExpect(redirectedUrl("/?message=" + HomeMessage.GITHUB_USER_NOT_FOUND.name() + "&login=" + login))
                .andExpect(status().is3xxRedirection());

        this.mockMvc.perform(get("/u/" + login + "/status"))
                .andExpect(jsonPath("$.state").value(GitHubUserStatus.NOT_FOUND.name()))
                .andExpect(jsonPath("$.message").value(HomeMessage.GITHUB_USER_NOT_FOUND.name()))
                .andExpect(jsonPath("$.attemptedLogin").value(login))
                .andExpect(status().isOk());
    }

    @Test
    public void shouldRedirectHomeWhenGitHubIsUnavailable() throws Exception {
        String login = "unavailable-user";
        this.fakeGitHubClient.setUserFailure(login, GitHubClientFailure.UNAVAILABLE);

        this.mockMvc.perform(get("/u/" + login))
                .andExpect(model().attribute("login", login))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilFailed(login, USER_TIMEOUT);

        this.mockMvc.perform(get("/u/" + login))
                .andExpect(redirectedUrl("/?message=" + HomeMessage.GITHUB_UNAVAILABLE.name() + "&login=" + login))
                .andExpect(status().is3xxRedirection());

        this.mockMvc.perform(get("/u/" + login + "/status"))
                .andExpect(jsonPath("$.state").value(GitHubUserStatus.FAILED.name()))
                .andExpect(jsonPath("$.message").value(HomeMessage.GITHUB_UNAVAILABLE.name()))
                .andExpect(jsonPath("$.attemptedLogin").value(login))
                .andExpect(status().isOk());
    }

    @Test
    public void shouldRedirectToCanonicalLoginAfterFetch() throws Exception {
        String attemptedLogin = "canonicalcase";
        configureSuccess(attemptedLogin, CANONICAL_INFO);

        this.mockMvc.perform(get("/u/" + attemptedLogin))
                .andExpect(model().attribute("login", attemptedLogin))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilFetched(attemptedLogin, USER_TIMEOUT);

        this.mockMvc.perform(get("/u/" + attemptedLogin))
                .andExpect(redirectedUrl("/u/" + CANONICAL_INFO.login()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    public void shouldExposeStatusTransitionFromNewToFetched() throws Exception {
        String login = STATUS_INFO.login();
        long retryAfterEpochMillis = Instant.now().plus(Duration.ofMinutes(10)).toEpochMilli();
        this.fakeGitHubClient.setUserRateLimited(login, retryAfterEpochMillis);

        this.mockMvc.perform(get("/u/" + login + "/status"))
                .andExpect(jsonPath("$.state").value(GitHubUserStatus.NEW.name()))
                .andExpect(jsonPath("$.attemptedLogin").value(login))
                .andExpect(status().isOk());

        configureSuccess(login, STATUS_INFO);

        waitUntilFetched(login, USER_TIMEOUT);

        this.mockMvc.perform(get("/u/" + login + "/status"))
                .andExpect(jsonPath("$.state").value(GitHubUserStatus.FETCHED.name()))
                .andExpect(jsonPath("$.canonicalLogin").value(STATUS_INFO.login()))
                .andExpect(jsonPath("$.attemptedLogin").value(login))
                .andExpect(status().isOk());
    }

    @Test
    public void shouldRenderFetchedUserWithoutFetchingPage() throws Exception {
        String login = CACHED_INFO.login();
        configureSuccess(login, CACHED_INFO);

        this.mockMvc.perform(get("/u/" + login))
                .andExpect(model().attribute("login", login))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilFetched(login, USER_TIMEOUT);

        this.mockMvc.perform(get("/u/" + login))
                .andExpect(model().attribute("login", login))
                .andExpect(model().attribute("name", CACHED_INFO.name()))
                .andExpect(model().attribute("bio", CACHED_INFO.bio()))
                .andExpect(model().attribute("followers", CACHED_INFO.followers()))
                .andExpect(model().attributeExists("fetchedAt"))
                .andExpect(model().attributeExists("photoDataUrl"))
                .andExpect(view().name("user"))
                .andExpect(status().isOk());

        assertEquals(GitHubUserStatus.FETCHED, this.gitHubUserRepository.findByLoginLower(login).orElseThrow().getStatus());
    }

    private void configureSuccess(String requestedLogin, GitHubUserInfo info) {
        this.fakeGitHubClient.setUserSuccess(requestedLogin, info);
        this.fakeGitHubClient.setAvatarSuccess(info.avatarUrl(), PNG_AVATAR);
    }

}
