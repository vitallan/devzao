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
@Table(name = "git_monthly_activity")
public class GitMonthlyActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_user_id", nullable = false)
    private GitHubUser gitHubUser;

    @Column(name = "activity_month", nullable = false, length = 7)
    private String activityMonth;

    @Column(name = "commit_count", nullable = false)
    private int commitCount;

    protected GitMonthlyActivity() {
    }

    public GitMonthlyActivity(GitHubUser gitHubUser, String activityMonth, int commitCount) {
        this.gitHubUser = gitHubUser;
        this.activityMonth = activityMonth;
        this.commitCount = commitCount;
    }

    public Long getId() {
        return this.id;
    }

    public GitHubUser getGitHubUser() {
        return this.gitHubUser;
    }

    public String getActivityMonth() {
        return this.activityMonth;
    }

    public int getCommitCount() {
        return this.commitCount;
    }

    public void setCommitCount(int commitCount) {
        this.commitCount = commitCount;
    }
}
