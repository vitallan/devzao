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
@Table(name = "git_commit_analysis")
public class GitCommitAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_user_id", nullable = false)
    private GitHubUser gitHubUser;

    @Column(name = "total_commits", nullable = false)
    private int totalCommits;

    @Column(name = "total_merge_commits", nullable = false)
    private int totalMergeCommits;

    @Column(name = "truncated", nullable = false)
    private boolean truncated;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    protected GitCommitAnalysis() {
    }

    public GitCommitAnalysis(GitHubUser gitHubUser) {
        this.gitHubUser = gitHubUser;
        this.analyzedAt = Instant.now();
    }

    public Long getId() {
        return this.id;
    }

    public GitHubUser getGitHubUser() {
        return this.gitHubUser;
    }

    public int getTotalCommits() {
        return this.totalCommits;
    }

    public void setTotalCommits(int totalCommits) {
        this.totalCommits = totalCommits;
    }

    public int getTotalMergeCommits() {
        return this.totalMergeCommits;
    }

    public void setTotalMergeCommits(int totalMergeCommits) {
        this.totalMergeCommits = totalMergeCommits;
    }

    public boolean isTruncated() {
        return this.truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public Instant getAnalyzedAt() {
        return this.analyzedAt;
    }
}
