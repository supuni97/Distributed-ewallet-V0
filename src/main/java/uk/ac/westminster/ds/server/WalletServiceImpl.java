package uk.ac.westminster.ds.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import uk.ac.westminster.ds.ewallet.grpc.*;
import uk.ac.westminster.ds.store.AccountStore;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class WalletServiceImpl extends WalletServiceGrpc.WalletServiceImplBase {

    private final AccountStore store;
    private final AtomicBoolean isLeader;

    // Milestone 3
    private final int myPort;
    private final List<Integer> replicaPorts;

    public WalletServiceImpl(AccountStore store, AtomicBoolean isLeader, int myPort, List<Integer> replicaPorts) {
        this.store = store;
        this.isLeader = isLeader;
        this.myPort = myPort;
        this.replicaPorts = replicaPorts;
    }

    private boolean ensureLeader(StreamObserver<?> responseObserver) {
        if (!isLeader.get()) {
            ((StreamObserver<Object>) responseObserver).onError(
                    Status.FAILED_PRECONDITION
                            .withDescription("NOT_LEADER")
                            .asRuntimeException()
            );
            return false;
        }
        return true;
    }

    // ===== Milestone 3: replication helpers =====

    @FunctionalInterface
    private interface ReplicationCall {
        void run(ReplicationServiceGrpc.ReplicationServiceBlockingStub stub);
    }

    private void callFollower(int port, ReplicationCall call) {
        ManagedChannel ch = null;
        try {
            ch = ManagedChannelBuilder.forAddress("localhost", port)
                    .usePlaintext()
                    .build();

            ReplicationServiceGrpc.ReplicationServiceBlockingStub repl =
                    ReplicationServiceGrpc.newBlockingStub(ch);

            call.run(repl);

        } catch (Exception e) {
            // For Milestone 3: log and continue (best-effort replication)
            System.err.println("Replication failed to follower localhost:" + port + " -> " + e.getMessage());
        } finally {
            if (ch != null) {
                ch.shutdown();
                try { ch.awaitTermination(1, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void replicateCreate(String accountId) {
        for (int port : replicaPorts) {
            if (port == myPort) continue;
            callFollower(port, stub -> stub.replicateCreateAccount(
                    CreateAccountRequest.newBuilder().setAccountId(accountId).build()
            ));
        }
    }

    private void replicateDeposit(String accountId, double amount) {
        for (int port : replicaPorts) {
            if (port == myPort) continue;
            callFollower(port, stub -> stub.replicateDeposit(
                    AmountRequest.newBuilder().setAccountId(accountId).setAmount(amount).build()
            ));
        }
    }

    private void replicateWithdraw(String accountId, double amount) {
        for (int port : replicaPorts) {
            if (port == myPort) continue;
            callFollower(port, stub -> stub.replicateWithdraw(
                    AmountRequest.newBuilder().setAccountId(accountId).setAmount(amount).build()
            ));
        }
    }

    // ===== WalletService RPCs =====

    @Override
    public void createAccount(CreateAccountRequest request, StreamObserver<CreateAccountResponse> responseObserver) {
        if (!ensureLeader(responseObserver)) return;

        String id = request.getAccountId().trim();
        boolean created = store.createAccount(id);

        // replicate (idempotent on followers)
        replicateCreate(id);

        CreateAccountResponse response = CreateAccountResponse.newBuilder()
                .setCreated(created)
                .setMessage(created ? "Account created" : "Account already exists")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getBalance(BalanceRequest request, StreamObserver<BalanceResponse> responseObserver) {
        // Keep reads allowed on any replica (same as your current code :contentReference[oaicite:4]{index=4})
        String id = request.getAccountId().trim();
        Double balance = store.getBalance(id);

        BalanceResponse response = (balance == null)
                ? BalanceResponse.newBuilder().setFound(false).setBalance(0).setMessage("Account not found").build()
                : BalanceResponse.newBuilder().setFound(true).setBalance(balance).setMessage("OK").build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void deposit(AmountRequest request, StreamObserver<AmountResponse> responseObserver) {
        if (!ensureLeader(responseObserver)) return;

        String id = request.getAccountId().trim();
        double amount = request.getAmount();

        boolean ok = store.deposit(id, amount);
        if (ok) {
            replicateDeposit(id, amount);
        }

        Double bal = store.getBalance(id);

        AmountResponse response = AmountResponse.newBuilder()
                .setOk(ok)
                .setBalance(bal == null ? 0 : bal)
                .setMessage(ok ? "Deposit successful" : "Deposit failed")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void withdraw(AmountRequest request, StreamObserver<AmountResponse> responseObserver) {
        if (!ensureLeader(responseObserver)) return;

        String id = request.getAccountId().trim();
        double amount = request.getAmount();

        boolean ok = store.withdraw(id, amount);
        if (ok) {
            replicateWithdraw(id, amount);
        }

        Double bal = store.getBalance(id);

        AmountResponse response = AmountResponse.newBuilder()
                .setOk(ok)
                .setBalance(bal == null ? 0 : bal)
                .setMessage(ok ? "Withdraw successful" : "Withdraw failed (insufficient funds or account missing)")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private void replicateTransfer(String from, String to, double amount) {
        for (int port : replicaPorts) {
            if (port == myPort) continue;

            callFollower(port, stub -> stub.replicateTransfer(
                    TransferRequest.newBuilder()
                            .setFromAccount(from)
                            .setToAccount(to)
                            .setAmount(amount)
                            .build()
            ));
        }

    }

    @Override
    public void transfer(TransferRequest request,
                         StreamObserver<TransferResponse> responseObserver) {

        if (!ensureLeader(responseObserver)) return;

        String from = request.getFromAccount().trim();
        String to = request.getToAccount().trim();
        double amount = request.getAmount();

        boolean ok = store.transfer(from, to, amount);

        if (ok) {
            replicateTransfer(from, to, amount);
        }

        TransferResponse response = TransferResponse.newBuilder()
                .setOk(ok)
                .setMessage(ok ? "Transfer successful"
                        : "Transfer failed (invalid account or insufficient funds)")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }


}
