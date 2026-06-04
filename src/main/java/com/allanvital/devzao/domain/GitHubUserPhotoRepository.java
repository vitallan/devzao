package com.allanvital.devzao.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
* @author Allan Vital (https://allanvital.com)
  */
public interface GitHubUserPhotoRepository extends JpaRepository<GitHubUserPhoto, Long> {

    Optional<GitHubUserPhoto> findByGitHubUserId(Long gitHubUserId);

}
