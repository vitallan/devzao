package com.allanvital.devzao.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
* @author Allan Vital (https://allanvital.com)
  */
public interface GitCommitAnalysisRepository extends JpaRepository<GitCommitAnalysis, Long> {

    Optional<GitCommitAnalysis> findByGitHubUser_Id(Long gitHubUserId);
}
