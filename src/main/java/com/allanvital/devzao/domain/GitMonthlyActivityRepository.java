package com.allanvital.devzao.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
* @author Allan Vital (https://allanvital.com)
  */
public interface GitMonthlyActivityRepository extends JpaRepository<GitMonthlyActivity, Long> {

    List<GitMonthlyActivity> findByGitHubUser_IdOrderByYearMonthAsc(Long gitHubUserId);

    void deleteByGitHubUser_Id(Long gitHubUserId);
}
