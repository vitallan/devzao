package com.allanvital.devzao.git;

import java.util.List;

/**
* @author Allan Vital (https://allanvital.com)
  */
public record GitCommitWalkResult(
        List<CommitInfo> commits,
        boolean truncated
) {
}
