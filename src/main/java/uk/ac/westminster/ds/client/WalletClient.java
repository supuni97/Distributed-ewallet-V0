package uk.ac.westminster.ds.client;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import uk.ac.westminster.ds.ewallet.grpc.*;
import uk.ac.westminster.ds.nameservice.NameServiceClient;

import java.io.IOException;

public class WalletClient {

    public static final String NAME_SERVICE_ADDRESS = "http://localhost:2379";
    public static final String SERVICE_NAME = "WalletService";

    // Milestone 4: two partitions
    public static final int NUM_PARTITIONS = 2;

    private ManagedChannel channel;
    private WalletServiceGrpc.WalletServiceBlockingStub stub;

    private String host;
    private int port;

    public WalletClient() {}

    // Deterministic partition function (same input -> same shard)
    private int partitionFor(String accountId) {
        return Math.abs(accountId.hashCode()) % NUM_PARTITIONS;
    }

    // etcd key for that partition leader
    private String serviceKeyForAccount(String accountId) {
        int p = partitionFor(accountId);
        return SERVICE_NAME + "/p" + p;
    }

    private void fetchServerDetails(String serviceKey) throws IOException, InterruptedException {
        System.out.println("Searching for details of service: " + serviceKey);
        NameServiceClient ns = new NameServiceClient(NAME_SERVICE_ADDRESS);
        NameServiceClient.ServiceDetails details = ns.findService(serviceKey);
        host = details.getIPAddress();
        port = details.getPort();
    }

    private void initializeConnection() {
        System.out.println("Connecting to server at " + host + ":" + port);
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        stub = WalletServiceGrpc.newBlockingStub(channel);
        channel.getState(true);
    }

    private void ensureReady(String serviceKey) throws IOException, InterruptedException {
        if (channel == null) {
            fetchServerDetails(serviceKey);
            initializeConnection();
        }

        ConnectivityState state = channel.getState(true);
        while (state != ConnectivityState.READY) {
            System.out.println("Service unavailable, looking for a service provider.");
            close(); // close old channel before reconnect
            fetchServerDetails(serviceKey);
            initializeConnection();
            Thread.sleep(2000);
            state = channel.getState(true);
        }
    }

    public void demoCalls(String accountId) throws IOException, InterruptedException {
        String serviceKey = serviceKeyForAccount(accountId);
        System.out.println("Using partition key: " + serviceKey);

        ensureReady(serviceKey);

        try {
            System.out.println(stub.createAccount(
                    CreateAccountRequest.newBuilder().setAccountId(accountId).build()
            ).getMessage());

            System.out.println(stub.deposit(
                    AmountRequest.newBuilder().setAccountId(accountId).setAmount(100).build()
            ).getMessage());

            System.out.println("Balance = " + stub.getBalance(
                    BalanceRequest.newBuilder().setAccountId(accountId).build()
            ).getBalance());

        } catch (io.grpc.StatusRuntimeException e) {
            // If we hit a follower, rediscover leader for that partition
            if (e.getStatus().getDescription() != null &&
                    e.getStatus().getDescription().contains("NOT_LEADER")) {

                System.out.println("Hit follower. Rediscovering leader via etcd...");
                close();
                fetchServerDetails(serviceKey);
                initializeConnection();

                // retry once
                System.out.println(stub.getBalance(
                        BalanceRequest.newBuilder().setAccountId(accountId).build()
                ).getBalance());
                return;
            }

            throw e;
        }
    }

    public void close() {
        if (channel != null) {
            channel.shutdownNow();
        }
        channel = null;
        stub = null;
    }
}
