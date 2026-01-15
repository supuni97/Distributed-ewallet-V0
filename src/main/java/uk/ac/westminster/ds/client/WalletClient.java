package uk.ac.westminster.ds.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import uk.ac.westminster.ds.ewallet.grpc.*;

public class WalletClient {

    public static void run(String host, int port) {

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        WalletServiceGrpc.WalletServiceBlockingStub stub =
                WalletServiceGrpc.newBlockingStub(channel);

        System.out.println(
                stub.createAccount(
                        CreateAccountRequest.newBuilder()
                                .setAccountId("alice")
                                .build()
                ).getMessage()
        );

        System.out.println(
                stub.deposit(
                        AmountRequest.newBuilder()
                                .setAccountId("alice")
                                .setAmount(100)
                                .build()
                ).getMessage()
        );

        System.out.println(
                stub.getBalance(
                        BalanceRequest.newBuilder()
                                .setAccountId("alice")
                                .build()
                ).getBalance()
        );

        channel.shutdown();
    }
}
