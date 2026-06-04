package com.allanvital.devzao.async;

import java.io.Serializable;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record UserFollowerPropagationMessage(Long gitHubUserId) implements Serializable {

}
