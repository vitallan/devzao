package com.allanvital.devzao.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
* @author Allan Vital (https://allanvital.com)
  */
public interface GitWeekdayActivityRepository extends JpaRepository<GitWeekdayActivity, Long> {

    List<GitWeekdayActivity> findByGitHubUser_IdOrderByDayOfWeekAsc(Long gitHubUserId);

    void deleteByGitHubUser_Id(Long gitHubUserId);
}
