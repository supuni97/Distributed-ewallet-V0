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
    private final int myPort;
    private final List<Integer> replicaPorts;

    public WalletServiceImpl(AccountStore store,
                             AtomicBoolean isLeader,
                             int myPort,
                             List<Integer> replicaPorts) {
        this.store = store;
        this.isLeader = isLeader;
        this.myPort = myPort;
        this.replicaPorts = replicaPorts;
    }

    private boolean ensureLeader(StreamObserver<?> responseObserver) {
        if (!isLeader.get()) {
            responseObserver.onError(
                    Status.FAILED_PRECONDITION
                            .withDescription("NOT_LEADER")
                            .asRuntimeException());
            return false;
        }
        return true;
    }

    @FunctionalInterface
    private interface ReplicationCall {
        void run(ReplicationServiceGrpc.ReplicationServiceBlockingStub stub);
    }

    private void callFollower(int port, ReplicationCall call) {
        ManagedChannel ch = null;
        try {
            ch = ManagedChannelBuilder
                    .forAddress("localhost", port)
                    .usePlaintext()
                    .build();

            ReplicationServiceGrpc.ReplicationServiceBlockingStub stub =
                    ReplicationServiceGrpc.newBlockingStub(ch);

            call.run(stub);

        } catch (Exception e) {
            System.err.println("Replication failed to port " + port + ": " + e.getMessage());
        } finally {
            if (ch != null) {
                ch.shutdown();
                try {
                    ch.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private void replicateCreateAccount(String accountId) {
        for (int port : replicaPorts) {
            if (port == myPort) continue;
            callFollower(port, stub ->
                    stub.replicateCreateAccount(
                            CreateAccountRequest.newBuilder()
                                    .setAccountId(accountId)
                                    .build()));
        }
    }

    private void replicateDeposit(String accountId, double amount) {
        for (int port : replicaPorts) {
            if (port == myPort) continue;
            callFollower(port, stub ->
                    stub.replicateDeposit(
                            AmountRequest.newBuilder()
                                    .setAccountId(accountId)
                                    .setAmount(amount)
                                    .build()));
        }
    }

    private void replicateWithdraw(String accountId, double amount) {
        for (int port : replicaPorts) {
            if (port == myPort) continue;
            callFollower(port, stub ->
                    stub.replicateWithdraw(
                            AmountRequest.newBuilder()
                                    .setAccountId(accountId)
                                    .setAmount(amount)
                                    .build()));
        }
    }

    private void replicateTransfer(String from, String to, double amount) {
        for (int port : replicaPorts) {
            if (port == myPort) continue;
            callFollower(port, stub ->
                    stub.replicateTransfer(
                            TransferRequest.newBuilder()
                                    .setFromAccount(from)
                                    .setToAccount(to)
                                    .setAmount(amount)
                                    .build()));
        }
    }

    @Override
    public void createAccount(CreateAccountRequest request,
                              StreamObserver<CreateAccountResponse> responseObserver) {

        if (!ensureLeader(responseObserver)) return;

        boolean created = store.createAccount(request.getAccountId());

        if (created) {
            replicateCreateAccount(request.getAccountId());
        }

        responseObserver.onNext(
                CreateAccountResponse.newBuilder()
                        .setCreated(created)
                        .setMessage(created ? "Account created" : "Account already exists")
                        .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getBalance(BalanceRequest request,
                           StreamObserver<BalanceResponse> responseObserver) {

        Double bal = store.getBalance(request.getAccountId());

        if (bal == null) {
            responseObserver.onNext(
                    BalanceResponse.newBuilder()
                            .setFound(false)
                            .setBalance(0)
                            .setMessage("Account not found")
                            .build());
        } else {
            responseObserver.onNext(
                    BalanceResponse.newBuilder()
                            .setFound(true)
                            .setBalance(bal)
                            .setMessage("OK")
                            .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void deposit(AmountRequest request,
                        StreamObserver<AmountResponse> responseObserver) {

        if (!ensureLeader(responseObserver)) return;

        boolean ok = store.deposit(
                request.getAccountId(),
                request.getAmount());

        if (ok) {
            replicateDeposit(request.getAccountId(), request.getAmount());
        }

        responseObserver.onNext(
                AmountResponse.newBuilder()
                        .setOk(ok)
                        .setBalance(
                                store.getBalance(request.getAccountId()) == null
                                        ? 0
                                        : store.getBalance(request.getAccountId()))
                        .setMessage(ok ? "Deposit successful" : "Account not found")
                        .build());
        responseObserver.onCompleted();
    }

    @Override
    public void withdraw(AmountRequest request,
                         StreamObserver<AmountResponse> responseObserver) {

        if (!ensureLeader(responseObserver)) return;

        boolean ok = store.withdraw(
                request.getAccountId(),
                request.getAmount());

        if (ok) {
            replicateWithdraw(request.getAccountId(), request.getAmount());
        }

        responseObserver.onNext(
                AmountResponse.newBuilder()
                        .setOk(ok)
                        .setBalance(
                                store.getBalance(request.getAccountId()) == null
                                        ? 0
                                        : store.getBalance(request.getAccountId()))
                        .setMessage(ok ? "Withdraw successful" : "Insufficient funds or account not found")
                        .build());
        responseObserver.onCompleted();
    }

    @Override
    public void transfer(TransferRequest request,
                         StreamObserver<TransferResponse> responseObserver) {

        if (!ensureLeader(responseObserver)) return;

        boolean ok = store.transfer(
                request.getFromAccount(),
                request.getToAccount(),
                request.getAmount());

        if (ok) {
            replicateTransfer(
                    request.getFromAccount(),
                    request.getToAccount(),
                    request.getAmount());
        }

        responseObserver.onNext(
                TransferResponse.newBuilder()
                        .setOk(ok)
                        .setMessage(ok
                                ? "Transfer successful"
                                : "Transfer failed (invalid account or insufficient funds)")
                        .build());
        responseObserver.onCompleted();
    }

    @Override
    public void prepareTransfer(TransferRequest request,
                                StreamObserver<PrepareResponse> responseObserver) {

        boolean ok = store.prepareDebit(
                request.getFromAccount(),
                request.getAmount());

        responseObserver.onNext(
                PrepareResponse.newBuilder()
                        .setOk(ok)
                        .setMessage(ok ? "READY" : "INSUFFICIENT_FUNDS")
                        .build());
        responseObserver.onCompleted();
    }

    @Override
    public void commitTransfer(TransferRequest request,
                               StreamObserver<Ack> responseObserver) {

        store.commitDebit(request.getFromAccount());
        store.deposit(request.getToAccount(), request.getAmount());

        responseObserver.onNext(Ack.newBuilder().setOk(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void abortTransfer(TransferRequest request,
                              StreamObserver<Ack> responseObserver) {

        store.abortDebit(request.getFromAccount());

        responseObserver.onNext(Ack.newBuilder().setOk(true).build());
        responseObserver.onCompleted();
    }
}
