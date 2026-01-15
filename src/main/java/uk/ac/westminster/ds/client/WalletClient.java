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

    private ManagedChannel channel;
    private WalletServiceGrpc.WalletServiceBlockingStub stub;

    private String host;
    private int port;

    public WalletClient() throws InterruptedException, IOException {
        fetchServerDetails();
        initializeConnection();
    }

    private void fetchServerDetails() throws IOException, InterruptedException {
        NameServiceClient ns = new NameServiceClient(NAME_SERVICE_ADDRESS);
        NameServiceClient.ServiceDetails details = ns.findService(SERVICE_NAME);
        host = details.getIPAddress();
        port = details.getPort();
    }

    private void initializeConnection() {
        System.out.println("Connecting to server at " + host + ":" + port);
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        stub = WalletServiceGrpc.newBlockingStub(channel);
        channel.getState(true);
    }

    private void ensureReady() throws IOException, InterruptedException {
        ConnectivityState state = channel.getState(true);
        while (state != ConnectivityState.READY) {
            System.out.println("Service unavailable, looking for a service provider.");
            fetchServerDetails();
            initializeConnection();
            Thread.sleep(5000);
            state = channel.getState(true);
        }
    }

    public void demoCalls() throws IOException, InterruptedException {
        ensureReady();

        System.out.println(stub.createAccount(
                CreateAccountRequest.newBuilder().setAccountId("alice").build()
        ).getMessage());

        System.out.println(stub.deposit(
                AmountRequest.newBuilder().setAccountId("alice").setAmount(100).build()
        ).getMessage());

        System.out.println("Balance = " + stub.getBalance(
                BalanceRequest.newBuilder().setAccountId("alice").build()
        ).getBalance());
    }

    public void close() {
        if (channel != null) channel.shutdown();
    }
}
