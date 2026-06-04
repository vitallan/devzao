package com.allanvital.devzao.git;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record CommitInfo(
        long epochSecond,
        boolean isMerge
) {
}
