package com.allanvital.devzao.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public class TestRepoBuilder {

    public static TestRepoBuilder create() {
        return new TestRepoBuilder();
    }

    private final List<Instant> commits = new ArrayList<>();

    public TestRepoBuilder commit(String isoDateTime) {
        this.commits.add(Instant.parse(isoDateTime));
        return this;
    }

    public URI build() {
        try {
            Path repoDir = Files.createTempDirectory("test-bare-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteDirectory(repoDir.toFile())));

            try (Git git = Git.init().setBare(true).setDirectory(repoDir.toFile()).call()) {
                Repository repo = git.getRepository();
                ObjectId parentId = null;

                for (int i = 0; i < commits.size(); i++) {
                    Instant when = commits.get(i);
                    try (ObjectInserter inserter = repo.newObjectInserter()) {
                        String content = "content-" + i + "-" + when;
                        ObjectId blobId = inserter.insert(Constants.OBJ_BLOB, content.getBytes(StandardCharsets.UTF_8));

                        TreeFormatter tree = new TreeFormatter();
                        tree.append("file.txt", FileMode.REGULAR_FILE, blobId);
                        ObjectId treeId = inserter.insert(tree);

                        CommitBuilder commit = new CommitBuilder();
                        commit.setTreeId(treeId);

                        long epochMillis = when.toEpochMilli();
                        PersonIdent ident = new PersonIdent("Test", "test@test.com", epochMillis, 0);
                        commit.setAuthor(ident);
                        commit.setCommitter(ident);

                        if (parentId != null) {
                            commit.setParentIds(new ObjectId[]{parentId});
                        }

                        ObjectId commitId = inserter.insert(commit);
                        inserter.flush();
                        parentId = commitId;
                    }
                }

                if (parentId != null) {
                    try (RevWalk revWalk = new RevWalk(repo)) {
                        RevCommit head = revWalk.parseCommit(parentId);
                        RefUpdate ref = repo.updateRef(Constants.HEAD);
                        ref.setNewObjectId(head.getId());
                        ref.disableRefLog();
                        ref.update();
                    }
                }
            }

            return repoDir.toUri();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build test repo", e);
        }
    }

    private static void deleteDirectory(java.io.File file) {
        if (file == null || !file.exists()) {
            return;
        }
        java.io.File[] contents = file.listFiles();
        if (contents != null) {
            for (java.io.File child : contents) {
                deleteDirectory(child);
            }
        }
        file.delete();
    }
}
