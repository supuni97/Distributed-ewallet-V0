package uk.ac.westminster.ds;

import uk.ac.westminster.ds.client.WalletClient;
import uk.ac.westminster.ds.server.WalletServer;

public class Main {

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("  server <port> <replicaId>");
            System.out.println("  client");
            return;
        }

        if (args[0].equalsIgnoreCase("server")) {
            int port = Integer.parseInt(args[1]);
            String replicaId = args[2];
            WalletServer.start(port, replicaId);

        } else if (args[0].equalsIgnoreCase("client")) {
            WalletClient client = new WalletClient();
            client.demoCalls();
            client.close();
        }
    }
}
