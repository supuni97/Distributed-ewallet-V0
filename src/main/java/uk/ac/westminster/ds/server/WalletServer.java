package uk.ac.westminster.ds.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.zookeeper.ZooKeeper;
import uk.ac.westminster.ds.nameservice.NameServiceClient;
import uk.ac.westminster.ds.store.AccountStore;
import uk.ac.westminster.ds.zookeeper.LeaderElector;
import uk.ac.westminster.ds.zookeeper.ZkConnector;

import java.util.concurrent.atomic.AtomicBoolean;

public class WalletServer {

    public static final String NAME_SERVICE_ADDRESS = "http://localhost:2379";
    public static final String SERVICE_NAME = "WalletService";

    // ZooKeeper
    public static final String ZK_ADDRESS = "127.0.0.1:2181";
    public static final String ELECTION_PATH = "/ewallet/partition0/election";

    public static void start(int port, String replicaId) throws Exception {

        AccountStore store = new AccountStore();
        AtomicBoolean isLeader = new AtomicBoolean(false);

        // gRPC server starts on every replica
        Server server = ServerBuilder
                .forPort(port)
                .addService(new WalletServiceImpl(store, isLeader)) // pass leader flag
                .build()
                .start();

        System.out.println("Replica " + replicaId + " started on port " + port);

        // Connect to ZooKeeper and start leader election
        ZooKeeper zk = ZkConnector.connect(ZK_ADDRESS, 5000);

        LeaderElector elector = new LeaderElector(zk, ELECTION_PATH, replicaId);
        elector.setOnLeadershipChange(leaderNow -> {
            boolean old = isLeader.getAndSet(leaderNow);

            if (leaderNow && !old) {
                System.out.println(">>> I AM LEADER: " + replicaId);

                // Register ONLY the leader in etcd
                try {
                    NameServiceClient ns = new NameServiceClient(NAME_SERVICE_ADDRESS);
                    ns.registerService(SERVICE_NAME, "localhost", port, "grpc");
                    System.out.println("Registered leader in etcd: " + SERVICE_NAME + " -> localhost:" + port);
                } catch (Exception e) {
                    System.err.println("WARNING: etcd registration failed: " + e.getMessage());
                }
            }

            if (!leaderNow && old) {
                System.out.println(">>> I AM FOLLOWER NOW: " + replicaId);
            }
        });

        elector.startElection();

        server.awaitTermination();
    }
}
