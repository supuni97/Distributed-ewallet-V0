package uk.ac.westminster.ds;

import uk.ac.westminster.ds.client.WalletClient;
import uk.ac.westminster.ds.server.WalletServer;

public class Main {

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("  server <port> <replicaId> <partitionId>");
            System.out.println("  client [accountId]");
            return;
        }

        if (args[0].equalsIgnoreCase("server")) {
            if (args.length != 4) {
                System.out.println("Usage: server <port> <replicaId> <partitionId>");
                return;
            }

            int port = Integer.parseInt(args[1]);
            String replicaId = args[2];
            int partitionId = Integer.parseInt(args[3]);

            WalletServer.start(port, replicaId, partitionId);

        } else if (args[0].equalsIgnoreCase("client")) {
            String accountId = (args.length >= 2) ? args[1].trim() : "alice";

            WalletClient client = new WalletClient();
            client.demoCalls(accountId);
            client.close();

        } else {
            System.out.println("Unknown mode: " + args[0]);
        }
    }
}
