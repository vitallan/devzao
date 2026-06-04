package com.allanvital.devzao.workflow;

import com.allanvital.devzao.domain.GitHubRepository;
import com.allanvital.devzao.domain.GitHubUser;
import com.allanvital.devzao.github.dto.GitHubAvatar;
import com.allanvital.devzao.github.dto.GitHubRepositoryInfo;
import com.allanvital.devzao.github.dto.GitHubUserInfo;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public class RepositorySyncTest extends E2ETest {

    private static final Duration USER_TIMEOUT = Duration.ofSeconds(5);
    private static final GitHubAvatar PNG_AVATAR = new GitHubAvatar("png".getBytes(UTF_8), "image/png");
    private static final GitHubUserInfo REPOSITORY_USER_INFO = new GitHubUserInfo(
            300L,
            "repo-sync-user",
            "Repository Sync User",
            "repository sync scenario",
            11,
            URI.create("https://example.com/repo-sync-user.png")
    );
    private static final GitHubUserInfo EMPTY_REPOSITORY_USER_INFO = new GitHubUserInfo(
            301L,
            "empty-repo-user",
            "Empty Repository User",
            "empty repository scenario",
            12,
            URI.create("https://example.com/empty-repo-user.png")
    );
    private static final GitHubUserInfo SLOW_REPOSITORY_USER_INFO = new GitHubUserInfo(
            302L,
            "slow-repo-user",
            "Slow Repository User",
            "slow repository scenario",
            13,
            URI.create("https://example.com/slow-repo-user.png")
    );
    private static final GitHubUserInfo LANGUAGE_REPOSITORY_USER_INFO = new GitHubUserInfo(
            303L,
            "language-repo-user",
            "Language Repository User",
            "language repository scenario",
            14,
            URI.create("https://example.com/language-repo-user.png")
    );

    @Test
    public void shouldPaginatePersistNonForkRepositoriesDeleteMissingAndRenderTopFive() throws Exception {
        configureSuccess(REPOSITORY_USER_INFO);
        List<GitHubRepositoryInfo> pageOne = new ArrayList<>();
        pageOne.add(repositoryInfo(4000L, "fork-with-stars", true, 9999));
        pageOne.add(repositoryInfo(4001L, "top-one", false, 500));
        pageOne.add(repositoryInfo(4002L, "top-two", false, 400));
        pageOne.add(repositoryInfo(4003L, "top-three", false, 300));
        pageOne.add(repositoryInfo(4004L, "top-four", false, 200));
        for (int index = 0; index < 95; index++) {
            pageOne.add(repositoryInfo(4100L + index, "low-" + index, false, 1));
        }

        List<GitHubRepositoryInfo> pageTwo = List.of(
                repositoryInfo(4005L, "top-five", false, 100),
                repositoryInfo(4006L, "low-page-two-a", false, 2),
                repositoryInfo(4007L, "low-page-two-b", false, 2)
        );
        this.fakeGitHubClient.setRepositoryPage(REPOSITORY_USER_INFO.login(), 1, pageOne);
        this.fakeGitHubClient.setRepositoryPage(REPOSITORY_USER_INFO.login(), 2, pageTwo);

        this.mockMvc.perform(get("/u/" + REPOSITORY_USER_INFO.login()))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilFetched(REPOSITORY_USER_INFO.login(), USER_TIMEOUT);
        waitUntilRepositorySyncFetched(REPOSITORY_USER_INFO.login(), USER_TIMEOUT);

        GitHubUser user = this.gitHubUserRepository.findByLoginLower(REPOSITORY_USER_INFO.login()).orElseThrow();
        assertEquals(102L, this.gitHubRepositoryRepository.countByGitHubUser_Id(user.getId()));
        assertTrue(this.gitHubRepositoryRepository.findByGitHubUser_IdAndName(user.getId(), "fork-with-stars").isEmpty());

        GitHubRepository topOne = this.gitHubRepositoryRepository.findByGitHubUser_IdAndName(user.getId(), "top-one").orElseThrow();
        assertEquals("https://github.com/repo-sync-user/top-one.git", topOne.getCloneUrl());
        assertEquals("https://github.com/repo-sync-user/top-one", topOne.getHtmlUrl());

        List<GitHubRepository> topRepositories = this.gitHubRepositoryRepository.findTop5ByGitHubUser_IdOrderByStargazersCountDescNameAsc(user.getId());
        assertEquals(5, topRepositories.size());
        assertEquals("top-one", topRepositories.get(0).getName());
        assertEquals("top-two", topRepositories.get(1).getName());
        assertEquals("top-three", topRepositories.get(2).getName());
        assertEquals("top-four", topRepositories.get(3).getName());
        assertEquals("top-five", topRepositories.get(4).getName());

        this.mockMvc.perform(get("/u/" + REPOSITORY_USER_INFO.login()))
                .andExpect(content().string(containsString("data-repository-sync-state=\"FETCHED\"")))
                .andExpect(content().string(containsString("/repositories")))
                .andExpect(content().string(containsString("/languages")))
                .andExpect(content().string(containsString("Top starred repositories")))
                .andExpect(content().string(containsString("href=\"https://github.com/repo-sync-user/top-one\" target=\"_blank\" rel=\"noopener noreferrer\"")))
                .andExpect(content().string(containsString("<span class=\"repository-name\">top-one</span>")))
                .andExpect(content().string(containsString("<span class=\"repository-name\">top-one</span>")))
                .andExpect(content().string(containsString("repo-recency")))
                .andExpect(content().string(containsString("<span>500</span>")))
                .andExpect(content().string(containsString("<span class=\"repository-name\">top-five</span>")))
                .andExpect(content().string(not(containsString("fork-with-stars"))))
                .andExpect(content().string(containsString("<span class=\"repository-name\">top-one</span>")))
                .andExpect(content().string(containsString("background:#d97706")))
                .andExpect(content().string(containsString("Repositories</div>")))
                .andExpect(content().string(containsString("Total stars</div>")))
                .andExpect(content().string(containsString("Total forks</div>")))
                .andExpect(status().isOk());

        this.mockMvc.perform(get("/u/" + REPOSITORY_USER_INFO.login() + "/repositories"))
                .andExpect(content().string(containsString("data-repository-sync-state=\"FETCHED\"")))
                .andExpect(content().string(containsString("<span class=\"repository-name\">top-one</span>")))
                .andExpect(content().string(containsString("repo-recency")))
                .andExpect(content().string(containsString("<span>500</span>")))
                .andExpect(status().isOk());

        this.fakeGitHubClient.setRepositoryPage(REPOSITORY_USER_INFO.login(), 1, List.of(repositoryInfo(4001L, "top-one", false, 501)));
        this.gitHubRepositorySyncService.startSync(user.getId());

        waitUntilRepositorySyncFetched(REPOSITORY_USER_INFO.login(), USER_TIMEOUT);
        waitUntilRepositoryCount(user.getId(), 1L, USER_TIMEOUT);

        Optional<GitHubRepository> deletedRepository = this.gitHubRepositoryRepository.findByGitHubUser_IdAndName(user.getId(), "top-two");
        assertTrue(deletedRepository.isEmpty());
        assertEquals(501, this.gitHubRepositoryRepository.findByGitHubUser_IdAndName(user.getId(), "top-one").orElseThrow().getStargazersCount());
    }

    @Test
    public void shouldRenderEmptyRepositoryStateWhenNoNonForkRepositoriesExist() throws Exception {
        configureSuccess(EMPTY_REPOSITORY_USER_INFO);
        this.fakeGitHubClient.setRepositoryPage(EMPTY_REPOSITORY_USER_INFO.login(), 1, List.of());

        this.mockMvc.perform(get("/u/" + EMPTY_REPOSITORY_USER_INFO.login()))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilFetched(EMPTY_REPOSITORY_USER_INFO.login(), USER_TIMEOUT);
        waitUntilRepositorySyncFetched(EMPTY_REPOSITORY_USER_INFO.login(), USER_TIMEOUT);

        this.mockMvc.perform(get("/u/" + EMPTY_REPOSITORY_USER_INFO.login()))
                .andExpect(content().string(containsString("Top starred repositories")))
                .andExpect(content().string(containsString("data-repository-sync-state=\"FETCHED\"")))
                .andExpect(content().string(containsString("No public source repositories found.")))
                .andExpect(content().string(not(containsString("repository-link"))))
                .andExpect(content().string(containsString("Repositories</div>")))
                .andExpect(content().string(containsString("Total stars</div>")))
                .andExpect(content().string(containsString("Total forks</div>")))
                .andExpect(status().isOk());
    }

    @Test
    public void shouldKeepRepositoryPanePollingWhileRepositorySyncIsRunning() throws Exception {
        configureSuccess(SLOW_REPOSITORY_USER_INFO);
        this.fakeGitHubClient.setRepositoryRateLimited(
                SLOW_REPOSITORY_USER_INFO.login(),
                1,
                Instant.now().plus(Duration.ofMinutes(10)).toEpochMilli()
        );

        this.mockMvc.perform(get("/u/" + SLOW_REPOSITORY_USER_INFO.login()))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilFetched(SLOW_REPOSITORY_USER_INFO.login(), USER_TIMEOUT);

        this.mockMvc.perform(get("/u/" + SLOW_REPOSITORY_USER_INFO.login()))
                .andExpect(content().string(containsString("data-repository-sync-state=\"FETCHING\"")))
                .andExpect(content().string(containsString("Repository highlights are being prepared.")))
                .andExpect(content().string(containsString("/repositories")))
                .andExpect(content().string(containsString("/languages")))
                .andExpect(content().string(containsString("Repositories</div>")))
                .andExpect(content().string(containsString("Total stars</div>")))
                .andExpect(content().string(containsString("Total forks</div>")))
                .andExpect(status().isOk());

        this.mockMvc.perform(get("/u/" + SLOW_REPOSITORY_USER_INFO.login() + "/repositories"))
                .andExpect(content().string(containsString("data-repository-sync-state=\"FETCHING\"")))
                .andExpect(content().string(containsString("Repository highlights are being prepared.")))
                .andExpect(status().isOk());

        this.mockMvc.perform(get("/u/" + SLOW_REPOSITORY_USER_INFO.login() + "/languages"))
                .andExpect(content().string(containsString("data-repository-sync-state=\"FETCHING\"")))
                .andExpect(content().string(containsString("Language breakdown is being prepared.")))
                .andExpect(status().isOk());
    }

    @Test
    public void shouldRenderLanguageChartWithStableColorsAndOmitBlankLanguages() throws Exception {
        configureSuccess(LANGUAGE_REPOSITORY_USER_INFO);
        this.fakeGitHubClient.setRepositoryPage(LANGUAGE_REPOSITORY_USER_INFO.login(), 1, List.of(
                repositoryInfo(LANGUAGE_REPOSITORY_USER_INFO.login(), 5001L, "java-one", false, 10, "Java"),
                repositoryInfo(LANGUAGE_REPOSITORY_USER_INFO.login(), 5002L, "java-two", false, 9, "Java"),
                repositoryInfo(LANGUAGE_REPOSITORY_USER_INFO.login(), 5003L, "python-one", false, 8, "Python"),
                repositoryInfo(LANGUAGE_REPOSITORY_USER_INFO.login(), 5004L, "no-language", false, 7, null),
                repositoryInfo(LANGUAGE_REPOSITORY_USER_INFO.login(), 5005L, "blank-language", false, 6, " ")
        ));

        this.mockMvc.perform(get("/u/" + LANGUAGE_REPOSITORY_USER_INFO.login()))
                .andExpect(view().name("user-fetching"))
                .andExpect(status().isOk());

        waitUntilFetched(LANGUAGE_REPOSITORY_USER_INFO.login(), USER_TIMEOUT);
        waitUntilRepositorySyncFetched(LANGUAGE_REPOSITORY_USER_INFO.login(), USER_TIMEOUT);

        this.mockMvc.perform(get("/u/" + LANGUAGE_REPOSITORY_USER_INFO.login() + "/languages"))
                .andExpect(content().string(containsString("data-repository-sync-state=\"FETCHED\"")))
                .andExpect(content().string(containsString("conic-gradient(")))
                .andExpect(content().string(containsString("<span class=\"language-name\">Java</span>")))
                .andExpect(content().string(containsString("<span class=\"language-name\">Python</span>")))
                .andExpect(content().string(containsString("background: #f7c948")))
                .andExpect(content().string(containsString("<span>2</span>")))
                .andExpect(content().string(containsString("<span>1</span>")))
                .andExpect(content().string(not(containsString("no-language"))))
                .andExpect(content().string(not(containsString("blank-language"))))
                .andExpect(status().isOk());
    }

    private void configureSuccess(GitHubUserInfo info) {
        this.fakeGitHubClient.setUserSuccess(info.login(), info);
        this.fakeGitHubClient.setAvatarSuccess(info.avatarUrl(), PNG_AVATAR);
    }

    private GitHubRepositoryInfo repositoryInfo(long githubRepoId, String name, boolean fork, int stargazersCount) {
        return repositoryInfo(REPOSITORY_USER_INFO.login(), githubRepoId, name, fork, stargazersCount, "Java");
    }

    private GitHubRepositoryInfo repositoryInfo(String ownerLogin, long githubRepoId, String name, boolean fork, int stargazersCount, String language) {
        return new GitHubRepositoryInfo(
                githubRepoId,
                name,
                ownerLogin + "/" + name,
                "Repository " + name,
                URI.create("https://github.com/" + ownerLogin + "/" + name),
                URI.create("https://github.com/" + ownerLogin + "/" + name + ".git"),
                fork,
                stargazersCount,
                3,
                stargazersCount,
                language,
                false,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-02-01T00:00:00Z"),
                Instant.parse("2025-03-01T00:00:00Z"),
                false
        );
    }
}
