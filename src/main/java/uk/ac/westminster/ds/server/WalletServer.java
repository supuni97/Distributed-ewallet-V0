package uk.ac.westminster.ds.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.zookeeper.ZooKeeper;
import uk.ac.westminster.ds.nameservice.NameServiceClient;
import uk.ac.westminster.ds.store.AccountStore;
import uk.ac.westminster.ds.zookeeper.LeaderElector;
import uk.ac.westminster.ds.zookeeper.ZkConnector;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class WalletServer {

    public static final String NAME_SERVICE_ADDRESS = "http://localhost:2379";
    public static final String SERVICE_NAME = "WalletService";

    // ZooKeeper
    public static final String ZK_ADDRESS = "127.0.0.1:2181";

    // Two partitions, each with its own replica group
    private static final List<Integer> PARTITION_0_PORTS = List.of(50051, 50052, 50053);
    private static final List<Integer> PARTITION_1_PORTS = List.of(50061, 50062, 50063);

    public static void start(int port, String replicaId, int partitionId) throws Exception {

        AccountStore store = new AccountStore();
        AtomicBoolean isLeader = new AtomicBoolean(false);

        // Partition-specific election path and etcd key
        String electionPath = "/ewallet/partition" + partitionId + "/election";
        String serviceKey = SERVICE_NAME + "/p" + partitionId;

        // Pick correct replica group for this partition
        List<Integer> replicaPorts =
                (partitionId == 0) ? PARTITION_0_PORTS : PARTITION_1_PORTS;

        // gRPC server starts on every replica:
        // - WalletService (client-facing, leader-only for writes)
        // - ReplicationService (internal leader->followers)
        Server server = ServerBuilder
                .forPort(port)
                .addService(new WalletServiceImpl(store, isLeader, port, replicaPorts))
                .addService(new ReplicationServiceImpl(store))
                .build()
                .start();

        System.out.println("Replica " + replicaId + " started on port " + port + " (partition " + partitionId + ")");

        // Connect to ZooKeeper and start leader election
        ZooKeeper zk = ZkConnector.connect(ZK_ADDRESS, 5000);

        LeaderElector elector = new LeaderElector(zk, electionPath, replicaId);
        elector.setOnLeadershipChange(leaderNow -> {
            boolean old = isLeader.getAndSet(leaderNow);

            if (leaderNow && !old) {
                System.out.println(">>> I AM LEADER: " + replicaId + " (partition " + partitionId + ")");

                // Register ONLY the leader for THIS partition in etcd
                try {
                    NameServiceClient ns = new NameServiceClient(NAME_SERVICE_ADDRESS);
                    ns.registerService(serviceKey, "localhost", port, "grpc");
                    System.out.println("Registered leader in etcd: " + serviceKey + " -> localhost:" + port);
                } catch (Exception e) {
                    System.err.println("WARNING: etcd registration failed: " + e.getMessage());
                }
            }

            if (!leaderNow && old) {
                System.out.println(">>> I AM FOLLOWER NOW: " + replicaId + " (partition " + partitionId + ")");
            }
        });

        elector.startElection();
        server.awaitTermination();
    }
}
