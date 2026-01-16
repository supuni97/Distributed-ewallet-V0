package uk.ac.westminster.ds.server;

import io.grpc.stub.StreamObserver;
import uk.ac.westminster.ds.ewallet.grpc.*;
import uk.ac.westminster.ds.store.AccountStore;

/**
 * Internal replication endpoint.
 * Followers accept these calls from the leader and apply writes locally.
 */
public class ReplicationServiceImpl extends ReplicationServiceGrpc.ReplicationServiceImplBase {

    private final AccountStore store;

    public ReplicationServiceImpl(AccountStore store) {
        this.store = store;
    }

    @Override
    public void replicateCreateAccount(CreateAccountRequest request, StreamObserver<Ack> responseObserver) {
        store.createAccount(request.getAccountId().trim()); // idempotent
        responseObserver.onNext(Ack.newBuilder().setOk(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void replicateDeposit(AmountRequest request, StreamObserver<Ack> responseObserver) {
        store.deposit(request.getAccountId().trim(), request.getAmount());
        responseObserver.onNext(Ack.newBuilder().setOk(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void replicateWithdraw(AmountRequest request, StreamObserver<Ack> responseObserver) {
        store.withdraw(request.getAccountId().trim(), request.getAmount());
        responseObserver.onNext(Ack.newBuilder().setOk(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void replicateTransfer(TransferRequest request,
                                  StreamObserver<Ack> responseObserver) {

        store.transfer(
                request.getFromAccount().trim(),
                request.getToAccount().trim(),
                request.getAmount()
        );

        responseObserver.onNext(Ack.newBuilder().setOk(true).build());
        responseObserver.onCompleted();
    }

}
