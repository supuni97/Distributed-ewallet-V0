package uk.ac.westminster.ds.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import uk.ac.westminster.ds.nameservice.NameServiceClient;
import uk.ac.westminster.ds.store.AccountStore;

public class WalletServer {

    public static final String NAME_SERVICE_ADDRESS = "http://localhost:2379";
    public static final String SERVICE_NAME = "WalletService";

    public static void start(int port) throws Exception {

        AccountStore store = new AccountStore();

        Server server = ServerBuilder
                .forPort(port)
                .addService(new WalletServiceImpl(store))
                .build()
                .start();

        System.out.println("WalletServer started on port " + port);

        // Milestone 1: register with etcd (Tutorial 3 style)
        NameServiceClient ns = new NameServiceClient(NAME_SERVICE_ADDRESS);
        ns.registerService(SERVICE_NAME, "localhost", port, "grpc");
        System.out.println("Registered " + SERVICE_NAME + " in etcd");

        server.awaitTermination();
    }
}
