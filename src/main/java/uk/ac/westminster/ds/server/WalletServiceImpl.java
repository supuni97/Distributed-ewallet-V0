package uk.ac.westminster.ds.server;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import uk.ac.westminster.ds.ewallet.grpc.*;
import uk.ac.westminster.ds.store.AccountStore;

import java.util.concurrent.atomic.AtomicBoolean;

public class WalletServiceImpl extends WalletServiceGrpc.WalletServiceImplBase {

    private final AccountStore store;
    private final AtomicBoolean isLeader;

    public WalletServiceImpl(AccountStore store, AtomicBoolean isLeader) {
        this.store = store;
        this.isLeader = isLeader;
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

    @Override
    public void createAccount(CreateAccountRequest request, StreamObserver<CreateAccountResponse> responseObserver) {
        if (!ensureLeader(responseObserver)) return;

        String id = request.getAccountId().trim();
        boolean created = store.createAccount(id);

        CreateAccountResponse response = CreateAccountResponse.newBuilder()
                .setCreated(created)
                .setMessage(created ? "Account created" : "Account already exists")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getBalance(BalanceRequest request, StreamObserver<BalanceResponse> responseObserver) {
        // Reads can be served by any replica OR leader-only.
        // For now, keep it simple: allow reads on any replica.
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

        boolean ok = store.deposit(request.getAccountId().trim(), request.getAmount());
        Double bal = store.getBalance(request.getAccountId().trim());

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

        boolean ok = store.withdraw(request.getAccountId().trim(), request.getAmount());
        Double bal = store.getBalance(request.getAccountId().trim());

        AmountResponse response = AmountResponse.newBuilder()
                .setOk(ok)
                .setBalance(bal == null ? 0 : bal)
                .setMessage(ok ? "Withdraw successful" : "Withdraw failed (insufficient funds or account missing)")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
