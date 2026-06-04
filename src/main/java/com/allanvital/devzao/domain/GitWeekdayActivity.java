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
@Table(name = "git_weekday_activity")
public class GitWeekdayActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_user_id", nullable = false)
    private GitHubUser gitHubUser;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "commit_count", nullable = false)
    private int commitCount;

    protected GitWeekdayActivity() {
    }

    public GitWeekdayActivity(GitHubUser gitHubUser, int dayOfWeek, int commitCount) {
        this.gitHubUser = gitHubUser;
        this.dayOfWeek = dayOfWeek;
        this.commitCount = commitCount;
    }

    public Long getId() {
        return this.id;
    }

    public GitHubUser getGitHubUser() {
        return this.gitHubUser;
    }

    public int getDayOfWeek() {
        return this.dayOfWeek;
    }

    public int getCommitCount() {
        return this.commitCount;
    }

    public void setCommitCount(int commitCount) {
        this.commitCount = commitCount;
    }
}
