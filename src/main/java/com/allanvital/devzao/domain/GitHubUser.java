package com.allanvital.devzao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Entity
@Table(name = "github_user")
public class GitHubUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id")
    private Long githubId;

    @Column(name = "login", nullable = false)
    private String login;

    @Column(name = "login_lower", nullable = false, unique = true)
    private String loginLower;

    @Column(name = "name")
    private String name;

    @Column(name = "bio", length = 2000)
    private String bio;

    @Column(name = "followers")
    private Integer followers;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GitHubUserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "repository_sync_status")
    private GitHubRepositorySyncStatus repositorySyncStatus;

    @Column(name = "repository_sync_token")
    private String repositorySyncToken;

    @Column(name = "repository_sync_started_at")
    private Instant repositorySyncStartedAt;

    @Column(name = "repository_synced_at")
    private Instant repositorySyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "deep_analysis_status")
    private GitDeepAnalysisStatus deepAnalysisStatus;

    protected GitHubUser() {
    }

    public GitHubUser(String login, String loginLower, GitHubUserStatus status) {
        this.login = login;
        this.loginLower = loginLower;
        this.status = status;
    }

    public Long getId() {
        return this.id;
    }

    public Long getGithubId() {
        return this.githubId;
    }

    public void setGithubId(Long githubId) {
        this.githubId = githubId;
    }

    public String getLogin() {
        return this.login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getLoginLower() {
        return this.loginLower;
    }

    public void setLoginLower(String loginLower) {
        this.loginLower = loginLower;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBio() {
        return this.bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public Integer getFollowers() {
        return this.followers;
    }

    public void setFollowers(Integer followers) {
        this.followers = followers;
    }

    public Instant getFetchedAt() {
        return this.fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public GitHubUserStatus getStatus() {
        return this.status;
    }

    public void setStatus(GitHubUserStatus status) {
        this.status = status;
    }

    public GitHubRepositorySyncStatus getRepositorySyncStatus() {
        return this.repositorySyncStatus;
    }

    public void setRepositorySyncStatus(GitHubRepositorySyncStatus repositorySyncStatus) {
        this.repositorySyncStatus = repositorySyncStatus;
    }

    public String getRepositorySyncToken() {
        return this.repositorySyncToken;
    }

    public void setRepositorySyncToken(String repositorySyncToken) {
        this.repositorySyncToken = repositorySyncToken;
    }

    public Instant getRepositorySyncStartedAt() {
        return this.repositorySyncStartedAt;
    }

    public void setRepositorySyncStartedAt(Instant repositorySyncStartedAt) {
        this.repositorySyncStartedAt = repositorySyncStartedAt;
    }

    public Instant getRepositorySyncedAt() {
        return this.repositorySyncedAt;
    }

    public void setRepositorySyncedAt(Instant repositorySyncedAt) {
        this.repositorySyncedAt = repositorySyncedAt;
    }

    public GitDeepAnalysisStatus getDeepAnalysisStatus() {
        return this.deepAnalysisStatus;
    }

    public void setDeepAnalysisStatus(GitDeepAnalysisStatus deepAnalysisStatus) {
        this.deepAnalysisStatus = deepAnalysisStatus;
    }
}
