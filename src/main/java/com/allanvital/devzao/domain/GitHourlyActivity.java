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

/**
* @author Allan Vital (https://allanvital.com)
  */
@Entity
@Table(name = "git_hourly_activity")
public class GitHourlyActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_user_id", nullable = false)
    private GitHubUser gitHubUser;

    @Column(name = "commit_hour", nullable = false)
    private int hour;

    @Column(name = "commit_count", nullable = false)
    private int commitCount;

    protected GitHourlyActivity() {
    }

    public GitHourlyActivity(GitHubUser gitHubUser, int hour, int commitCount) {
        this.gitHubUser = gitHubUser;
        this.hour = hour;
        this.commitCount = commitCount;
    }

    public Long getId() {
        return this.id;
    }

    public GitHubUser getGitHubUser() {
        return this.gitHubUser;
    }

    public int getHour() {
        return this.hour;
    }

    public int getCommitCount() {
        return this.commitCount;
    }

    public void setCommitCount(int commitCount) {
        this.commitCount = commitCount;
    }
}
