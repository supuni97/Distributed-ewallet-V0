package uk.ac.westminster.ds.zookeeper;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class LeaderElector {

    private final ZooKeeper zk;
    private final String electionPath;
    private final String replicaId;
    private String myNodePath;

    private Consumer<Boolean> onLeadershipChange = isLeader -> {};

    public LeaderElector(ZooKeeper zk, String electionPath, String replicaId) {
        this.zk = zk;
        this.electionPath = electionPath;
        this.replicaId = replicaId;
    }

    public void setOnLeadershipChange(Consumer<Boolean> handler) {
        this.onLeadershipChange = handler;
    }

    public void startElection() throws Exception {
        ensurePath(electionPath);

        // Create ephemeral sequential znode
        String prefix = electionPath + "/replica-";
        myNodePath = zk.create(prefix,
                replicaId.getBytes(StandardCharsets.UTF_8),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);

        electLeader();
    }

    private void electLeader() throws Exception {
        List<String> children = zk.getChildren(electionPath, watchedEvent -> {
            // Re-run election if membership changes
            try { electLeader(); } catch (Exception e) { System.err.println("Election error: " + e); }
        });

        if (children.isEmpty()) return;

        Collections.sort(children);
        String leaderChild = children.get(0);
        String leaderPath = electionPath + "/" + leaderChild;

        boolean iAmLeader = myNodePath.equals(leaderPath);
        onLeadershipChange.accept(iAmLeader);
    }

    private void ensurePath(String path) throws Exception {
        Stat stat = zk.exists(path, false);
        if (stat == null) {
            String[] parts = path.split("/");
            String current = "";
            for (String p : parts) {
                if (p.isBlank()) continue;
                current += "/" + p;
                if (zk.exists(current, false) == null) {
                    try {
                        zk.create(current, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    } catch (KeeperException.NodeExistsException ignored) { }
                }
            }
        }
    }
}
