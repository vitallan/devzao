package com.allanvital.devzao.git.remote;

/**
* @author Allan Vital (https://allanvital.com)
  */
public class GitWalkFailedException extends RuntimeException {

    public GitWalkFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
