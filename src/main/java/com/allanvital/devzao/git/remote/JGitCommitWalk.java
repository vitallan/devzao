package com.allanvital.devzao.git.remote;

import com.allanvital.devzao.git.remote.GitCloneFailedException;
import com.allanvital.devzao.git.remote.GitWalkFailedException;
import com.allanvital.devzao.git.CommitInfo;
import com.allanvital.devzao.git.GitCommitWalkResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Component
public class JGitCommitWalk {

    private static final Logger log = LoggerFactory.getLogger(JGitCommitWalk.class);

    public GitCommitWalkResult walkCommits(URI cloneUrl, int maxDepth, Path tempDir, String tempPrefix) {
        Path repoDir = tempDir.resolve(tempPrefix);
        Git git = null;
        try {
            log.debug("Cloning bare shallow (depth={}) from {}", maxDepth, cloneUrl);
            git = Git.cloneRepository()
                    .setURI(cloneUrl.toString())
                    .setDirectory(repoDir.toFile())
                    .setBare(true)
                    .setDepth(maxDepth)
                    .setCloneAllBranches(false)
                    .setBranch("HEAD")
                    .call();

            Repository repository = git.getRepository();
            ObjectId headId = repository.resolve("HEAD");

            if (headId == null) {
                log.warn("No HEAD found for {}", cloneUrl);
                return new GitCommitWalkResult(List.of(), false);
            }

            List<CommitInfo> commits = new ArrayList<>();
            boolean truncated = false;

            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit head = revWalk.parseCommit(headId);
                revWalk.markStart(head);

                int count = 0;
                for (RevCommit commit : revWalk) {
                    if (count >= maxDepth) {
                        truncated = true;
                        break;
                    }
                    commits.add(new CommitInfo(
                            commit.getCommitTime(),
                            commit.getParentCount() > 1
                    ));
                    count++;
                }
            }

            commits.sort(Comparator.comparingLong(CommitInfo::epochSecond));
            log.debug("Walked {} commits from {}", commits.size(), cloneUrl);

            StoredConfig config = repository.getConfig();
            config.setBoolean("gc", null, "autogc", false);
            config.save();

            return new GitCommitWalkResult(commits, truncated);

        } catch (GitAPIException e) {
            throw new GitCloneFailedException("Failed to clone " + cloneUrl, e);
        } catch (IOException e) {
            throw new GitWalkFailedException("Failed to walk commits from " + cloneUrl, e);
        } finally {
            if (git != null) {
                git.close();
            }
            try {
                deleteDirectory(repoDir.toFile());
            } catch (Exception e) {
                log.debug("Failed to delete temp directory {}", repoDir, e);
            }
        }
    }

    private static void deleteDirectory(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File child : contents) {
                deleteDirectory(child);
            }
        }
        file.delete();
    }
}
