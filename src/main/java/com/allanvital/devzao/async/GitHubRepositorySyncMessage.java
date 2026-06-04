package com.allanvital.devzao.async;

import com.allanvital.devzao.github.GitHubApiCallCategory;
import java.io.Serializable;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record GitHubRepositorySyncMessage(Long gitHubUserId, int page, String syncToken, GitHubApiCallCategory category) implements Serializable {

}
