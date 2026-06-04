package com.allanvital.devzao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Entity
@Table(name = "github_repository")
public class GitHubRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_user_id", nullable = false)
    private GitHubUser gitHubUser;

    @Column(name = "github_repo_id", nullable = false, unique = true)
    private Long githubRepoId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "html_url", nullable = false, length = 1000)
    private String htmlUrl;

    @Column(name = "clone_url", nullable = false, length = 1000)
    private String cloneUrl;

    @Column(name = "stargazers_count", nullable = false)
    private Integer stargazersCount;

    @Column(name = "forks_count", nullable = false)
    private Integer forksCount;

    @Column(name = "watchers_count", nullable = false)
    private Integer watchersCount;

    @Column(name = "language")
    private String language;

    @Column(name = "archived", nullable = false)
    private Boolean archived;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "pushed_at")
    private Instant pushedAt;

    @Column(name = "commit_count")
    private Integer commitCount;

    @Column(name = "is_private", nullable = false)
    private Boolean isPrivate;

    @Column(name = "last_seen_sync_token", nullable = false)
    private String lastSeenSyncToken;

    protected GitHubRepository() {
    }

    public GitHubRepository(GitHubUser gitHubUser, Long githubRepoId) {
        this.gitHubUser = gitHubUser;
        this.githubRepoId = githubRepoId;
    }

    public Long getId() {
        return this.id;
    }

    public GitHubUser getGitHubUser() {
        return this.gitHubUser;
    }

    public Long getGithubRepoId() {
        return this.githubRepoId;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return this.fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHtmlUrl() {
        return this.htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public String getCloneUrl() {
        return this.cloneUrl;
    }

    public void setCloneUrl(String cloneUrl) {
        this.cloneUrl = cloneUrl;
    }

    public Integer getStargazersCount() {
        return this.stargazersCount;
    }

    public void setStargazersCount(Integer stargazersCount) {
        this.stargazersCount = stargazersCount;
    }

    public Integer getForksCount() {
        return this.forksCount;
    }

    public void setForksCount(Integer forksCount) {
        this.forksCount = forksCount;
    }

    public Integer getWatchersCount() {
        return this.watchersCount;
    }

    public void setWatchersCount(Integer watchersCount) {
        this.watchersCount = watchersCount;
    }

    public String getLanguage() {
        return this.language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Boolean getArchived() {
        return this.archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getPushedAt() {
        return this.pushedAt;
    }

    public void setPushedAt(Instant pushedAt) {
        this.pushedAt = pushedAt;
    }

    public Integer getCommitCount() {
        return this.commitCount;
    }

    public void setCommitCount(Integer commitCount) {
        this.commitCount = commitCount;
    }

    public Boolean getIsPrivate() {
        return this.isPrivate;
    }

    public void setIsPrivate(Boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public String getLastSeenSyncToken() {
        return this.lastSeenSyncToken;
    }

    public void setLastSeenSyncToken(String lastSeenSyncToken) {
        this.lastSeenSyncToken = lastSeenSyncToken;
    }
}
