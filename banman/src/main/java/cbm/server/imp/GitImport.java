package cbm.server.imp;

import cbm.server.db.BansDatabase;
import cbm.server.model.Ban;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.OrTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class GitImport {

    private static final Logger LOGGER = LogManager.getLogger();

    public static void main(String[] args) {
        final FileRepositoryBuilder builder = new FileRepositoryBuilder()
                .setGitDir(new File(args[0]))
                .readEnvironment()
                .findGitDir();

        try (final var bansDatabase = new BansDatabase(args[1]);
             final Repository repo = builder.build()) {

            LOGGER.info("Using git repo {}", repo);

            final List<BanFile> banFileIds = getBanFiles(repo);

            final ListIterator<BanFile> listIterator = banFileIds.listIterator(banFileIds.size());
            while (listIterator.hasPrevious()) {
                final BanFile banFile = listIterator.previous();
                LOGGER.info("Commit {}", banFile.timestamp());
                final ObjectLoader loader = repo.open(banFile.objectId);
                final byte[] bytes = loader.getBytes();

                if (bytes.length == 0)
                    continue;

                final String s = new String(bytes, StandardCharsets.UTF_8);
                final List<Ban> bans = ParseBan.parseBans(s);
                final BansDatabase.Stats stats = bansDatabase.storeBans(banFile.timestamp(), bans.stream(), true);
                LOGGER.info("Added {}, removed {} bans", stats.numAdded(), stats.numRemoved());
            }

            LOGGER.info("Processed {} commits", banFileIds.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NotNull
    private static List<BanFile> getBanFiles(Repository repo) throws IOException, GitAPIException {
        final Git git = new Git(repo);

        final List<BanFile> banFileIds = new ArrayList<>();

        final ObjectId head = repo.resolve(Constants.HEAD);

        for (RevCommit commit : git.log().add(head).call()) {
            final RevTree tree = commit.getTree();
            try (final TreeWalk treeWalk = new TreeWalk(repo)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(OrTreeFilter.create(PathFilter.create("bans.log"),
                                                       PathFilter.create("bans.jsonlines")));

                if (!treeWalk.next())
                    continue;

                final ObjectId objectId = treeWalk.getObjectId(0);
                banFileIds.add(new BanFile(commit.getCommitTime(), objectId));
            }
        }
        return banFileIds;
    }

    private static class BanFile {
        public final int commitTime;
        public final ObjectId objectId;

        private BanFile(int commitTime, ObjectId objectId) {
            this.commitTime = commitTime;
            this.objectId = objectId;
        }

        public Instant timestamp() {
            return Instant.ofEpochSecond(commitTime);
        }
    }
}
