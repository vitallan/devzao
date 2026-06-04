package com.allanvital.devzao.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
* @author Allan Vital (https://allanvital.com)
  */
public interface GitHubRepositoryRepository extends JpaRepository<GitHubRepository, Long> {

    Optional<GitHubRepository> findByGithubRepoId(Long githubRepoId);

    long countByGitHubUser_Id(Long gitHubUserId);

    List<GitHubRepository> findTop5ByGitHubUser_IdOrderByStargazersCountDescNameAsc(Long gitHubUserId);

    @Query("select repo from GitHubRepository repo where repo.gitHubUser.id = :gitHubUserId "
            + "and repo.commitCount > 0 order by repo.commitCount desc, repo.name asc")
    List<GitHubRepository> findTopByGitHubUser_IdOrderByCommitCountDescNameAsc(@Param("gitHubUserId") Long gitHubUserId, Pageable pageable);

    List<GitHubRepository> findByGitHubUser_IdAndIsPrivateFalse(Long gitHubUserId);

    Optional<GitHubRepository> findByGitHubUser_IdAndName(Long gitHubUserId, String name);

    @Query("select repo.language as language, count(repo.id) as repositoryCount "
            + "from GitHubRepository repo "
            + "where repo.gitHubUser.id = :gitHubUserId "
            + "and repo.language is not null "
            + "and trim(repo.language) <> '' "
            + "group by repo.language "
            + "order by count(repo.id) desc, repo.language asc")
    List<GitHubRepositoryLanguageCount> findLanguageCountsByGitHubUserId(@Param("gitHubUserId") Long gitHubUserId);

    @Modifying
    @Query("delete from GitHubRepository repo where repo.gitHubUser.id = :gitHubUserId and "
            + "(repo.lastSeenSyncToken is null or repo.lastSeenSyncToken <> :syncToken)")
    int deleteMissingFromSync(
            @Param("gitHubUserId") Long gitHubUserId,
            @Param("syncToken") String syncToken
    );

    @Query("select coalesce(sum(repo.stargazersCount), 0) from GitHubRepository repo where repo.gitHubUser.id = :gitHubUserId")
    long sumStargazersByGitHubUserId(@Param("gitHubUserId") Long gitHubUserId);

    @Query("select coalesce(sum(repo.forksCount), 0) from GitHubRepository repo where repo.gitHubUser.id = :gitHubUserId")
    long sumForksByGitHubUserId(@Param("gitHubUserId") Long gitHubUserId);
}
