package uk.ac.westminster.ds.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import uk.ac.westminster.ds.ewallet.grpc.*;
import uk.ac.westminster.ds.nameservice.NameServiceClient;

public class WalletClient {

    private static final int PARTITIONS = 2;
    private static final String SERVICE = "WalletService";

    private ManagedChannel channel;

    /* ---------------- Partition logic ---------------- */

    private int partitionFor(String id) {
        return Math.abs(id.hashCode()) % PARTITIONS;
    }

    private WalletServiceGrpc.WalletServiceBlockingStub stubFor(int partition)
            throws Exception {

        NameServiceClient ns = new NameServiceClient("http://localhost:2379");
        NameServiceClient.ServiceDetails sd =
                ns.findService(SERVICE + "/p" + partition);

        channel = ManagedChannelBuilder
                .forAddress(sd.getIPAddress(), sd.getPort())
                .usePlaintext()
                .build();

        return WalletServiceGrpc.newBlockingStub(channel);
    }

    /* ---------------- Milestone 4 demo ---------------- */

    public void demoCalls(String accountId) throws Exception {

        int p = partitionFor(accountId);
        WalletServiceGrpc.WalletServiceBlockingStub stub = stubFor(p);

        System.out.println(stub.createAccount(
                CreateAccountRequest.newBuilder()
                        .setAccountId(accountId)
                        .build()).getMessage());

        System.out.println(stub.deposit(
                AmountRequest.newBuilder()
                        .setAccountId(accountId)
                        .setAmount(100)
                        .build()).getMessage());

        System.out.println("Balance = " +
                stub.getBalance(
                        BalanceRequest.newBuilder()
                                .setAccountId(accountId)
                                .build()).getBalance());
    }

    /* ---------------- Milestone 6 transfer ---------------- */

    public void transfer(String from, String to, double amount) throws Exception {

        int pFrom = partitionFor(from);
        int pTo = partitionFor(to);

        WalletServiceGrpc.WalletServiceBlockingStub fromStub = stubFor(pFrom);
        WalletServiceGrpc.WalletServiceBlockingStub toStub = stubFor(pTo);

        TransferRequest req =
                TransferRequest.newBuilder()
                        .setFromAccount(from)
                        .setToAccount(to)
                        .setAmount(amount)
                        .build();

        // Same partition → normal transfer
        if (pFrom == pTo) {
            System.out.println(fromStub.transfer(req).getMessage());
            return;
        }

        PrepareResponse r1 = fromStub.prepareTransfer(req);

        if (r1.getOk()) {
            fromStub.commitTransfer(req);
            toStub.commitTransfer(req);
            System.out.println("2PC transfer committed");
        } else {
            fromStub.abortTransfer(req);
            System.out.println("2PC transfer aborted");
        }

    }

    /* ---------------- Cleanup ---------------- */

    public void close() {
        if (channel != null) {
            channel.shutdownNow();
        }
    }
}
