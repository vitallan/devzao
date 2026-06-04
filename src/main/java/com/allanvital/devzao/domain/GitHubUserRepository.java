package com.allanvital.devzao.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
* @author Allan Vital (https://allanvital.com)
  */
public interface GitHubUserRepository extends JpaRepository<GitHubUser, Long> {

    Optional<GitHubUser> findByLoginLower(String loginLower);

    @Query("SELECT u FROM GitHubUser u WHERE u.status <> :newStatus AND (u.fetchedAt IS NULL OR u.fetchedAt < :userCutoff OR u.repositorySyncedAt IS NULL OR u.repositorySyncedAt < :repoCutoff OR u.deepAnalysisStatus IS NULL OR u.deepAnalysisStatus <> :completed) ORDER BY u.fetchedAt ASC")
    List<GitHubUser> findStaleForRefresh(@Param("userCutoff") Instant userCutoff, @Param("repoCutoff") Instant repoCutoff, @Param("completed") GitDeepAnalysisStatus completed, @Param("newStatus") GitHubUserStatus newStatus, Pageable pageable);

    @Query(value = "SELECT * FROM github_user WHERE status IN ('FETCHED', 'STALE') ORDER BY RAND() LIMIT 1", nativeQuery = true)
    Optional<GitHubUser> findRandomFetchedUser();
}
