package uk.ac.westminster.ds.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import uk.ac.westminster.ds.store.AccountStore;

public class WalletServer {

    public static void start(int port) throws Exception {
        AccountStore store = new AccountStore();

        Server server = ServerBuilder
                .forPort(port)
                .addService(new WalletServiceImpl(store))
                .build()
                .start();

        System.out.println("WalletServer started on port " + port);

        server.awaitTermination();
    }
}
