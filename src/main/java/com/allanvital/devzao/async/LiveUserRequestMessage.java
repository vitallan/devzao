package com.allanvital.devzao.async;

import com.allanvital.devzao.github.GitHubApiCallCategory;
import java.io.Serializable;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record LiveUserRequestMessage(Long gitHubUserId, GitHubApiCallCategory category) implements Serializable {

}
