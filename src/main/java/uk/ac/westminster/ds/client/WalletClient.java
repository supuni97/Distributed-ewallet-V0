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
    public static final int NUM_PARTITIONS = 2;

    private ManagedChannel channel;
    private WalletServiceGrpc.WalletServiceBlockingStub stub;

    private String host;
    private int port;

    public WalletClient() {}

    // Deterministic partition mapping
    private int partitionFor(String accountId) {
        return Math.abs(accountId.hashCode()) % NUM_PARTITIONS;
    }

    private String serviceKeyForPartition(int partitionId) {
        return SERVICE_NAME + "/p" + partitionId;
    }

    private String serviceKeyForAccount(String accountId) {
        return serviceKeyForPartition(partitionFor(accountId));
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
            close();
            fetchServerDetails(serviceKey);
            initializeConnection();
            Thread.sleep(2000);
            state = channel.getState(true);
        }
    }

    private boolean isNotLeader(io.grpc.StatusRuntimeException e) {
        return e.getStatus().getDescription() != null
                && e.getStatus().getDescription().contains("NOT_LEADER");
    }

    private void rediscoverLeader(String serviceKey) throws IOException, InterruptedException {
        System.out.println("Hit follower. Rediscovering leader via etcd...");
        close();
        fetchServerDetails(serviceKey);
        initializeConnection();
    }

    // -------- Milestone 4 demo calls (create + deposit + balance) --------
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
            if (isNotLeader(e)) {
                rediscoverLeader(serviceKey);
                // retry once
                demoCalls(accountId);
                return;
            }
            throw e;
        }
    }

    // -------- Milestone 5: within-partition transfer --------
    public void transfer(String from, String to, double amount) throws IOException, InterruptedException {

        int pFrom = partitionFor(from);
        int pTo = partitionFor(to);

        // Milestone 5 is WITHIN partition only.
        if (pFrom != pTo) {
            System.out.println("CROSS_PARTITION_TRANSFER_NOT_SUPPORTED_IN_MILESTONE_5");
            System.out.println("from partition = p" + pFrom + ", to partition = p" + pTo);
            return;
        }

        String serviceKey = serviceKeyForPartition(pFrom);
        System.out.println("Using partition key: " + serviceKey);

        ensureReady(serviceKey);

        try {
            TransferResponse resp = stub.transfer(
                    TransferRequest.newBuilder()
                            .setFromAccount(from)
                            .setToAccount(to)
                            .setAmount(amount)
                            .build()
            );

            System.out.println(resp.getMessage());

            // show balances after transfer (useful for evidence)
            double fromBal = stub.getBalance(BalanceRequest.newBuilder().setAccountId(from).build()).getBalance();
            double toBal = stub.getBalance(BalanceRequest.newBuilder().setAccountId(to).build()).getBalance();
            System.out.println("After transfer:");
            System.out.println("  " + from + " = " + fromBal);
            System.out.println("  " + to + " = " + toBal);

        } catch (io.grpc.StatusRuntimeException e) {
            if (isNotLeader(e)) {
                rediscoverLeader(serviceKey);
                // retry once
                transfer(from, to, amount);
                return;
            }
            throw e;
        }
    }

    public void close() {
        if (channel != null) channel.shutdownNow();
        channel = null;
        stub = null;
    }
}
