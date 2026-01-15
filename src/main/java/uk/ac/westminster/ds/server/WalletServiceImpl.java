package uk.ac.westminster.ds.server;

import io.grpc.stub.StreamObserver;
import uk.ac.westminster.ds.store.AccountStore;
import uk.ac.westminster.ds.ewallet.grpc.*;

public class WalletServiceImpl extends WalletServiceGrpc.WalletServiceImplBase {

    private final AccountStore store;

    public WalletServiceImpl(AccountStore store) {
        this.store = store;
    }

    @Override
    public void createAccount(CreateAccountRequest request,
                              StreamObserver<CreateAccountResponse> responseObserver) {

        String id = request.getAccountId();
        boolean created = store.createAccount(id);

        CreateAccountResponse response = CreateAccountResponse.newBuilder()
                .setCreated(created)
                .setMessage(created ? "Account created" : "Account already exists")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getBalance(BalanceRequest request,
                           StreamObserver<BalanceResponse> responseObserver) {

        String id = request.getAccountId();
        Double balance = store.getBalance(id);

        BalanceResponse response = (balance == null)
                ? BalanceResponse.newBuilder()
                .setFound(false)
                .setBalance(0)
                .setMessage("Account not found")
                .build()
                : BalanceResponse.newBuilder()
                .setFound(true)
                .setBalance(balance)
                .setMessage("OK")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void deposit(AmountRequest request,
                        StreamObserver<AmountResponse> responseObserver) {

        boolean ok = store.deposit(
                request.getAccountId(),
                request.getAmount()
        );

        Double bal = store.getBalance(request.getAccountId());

        AmountResponse response = AmountResponse.newBuilder()
                .setOk(ok)
                .setBalance(bal == null ? 0 : bal)
                .setMessage(ok ? "Deposit successful" : "Deposit failed")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void withdraw(AmountRequest request,
                         StreamObserver<AmountResponse> responseObserver) {

        boolean ok = store.withdraw(
                request.getAccountId(),
                request.getAmount()
        );

        Double bal = store.getBalance(request.getAccountId());

        AmountResponse response = AmountResponse.newBuilder()
                .setOk(ok)
                .setBalance(bal == null ? 0 : bal)
                .setMessage(ok ? "Withdraw successful" : "Withdraw failed")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
