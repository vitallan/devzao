package com.allanvital.devzao.git.remote;

/**
* @author Allan Vital (https://allanvital.com)
  */
public class GitCloneFailedException extends RuntimeException {

    public GitCloneFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
