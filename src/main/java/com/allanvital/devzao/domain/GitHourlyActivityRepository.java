package com.allanvital.devzao.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
* @author Allan Vital (https://allanvital.com)
  */
public interface GitHourlyActivityRepository extends JpaRepository<GitHourlyActivity, Long> {

    List<GitHourlyActivity> findByGitHubUser_IdOrderByHourAsc(Long gitHubUserId);

    void deleteByGitHubUser_Id(Long gitHubUserId);
}
