package uk.ac.westminster.ds;

import uk.ac.westminster.ds.client.WalletClient;
import uk.ac.westminster.ds.server.WalletServer;

public class Main {

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            printUsage();
            return;
        }

        if (args[0].equalsIgnoreCase("server")) {
            if (args.length != 4) {
                printUsage();
                return;
            }

            int port = Integer.parseInt(args[1]);
            String replicaId = args[2];
            int partitionId = Integer.parseInt(args[3]);

            WalletServer.start(port, replicaId, partitionId);
            return;
        }

        if (args[0].equalsIgnoreCase("client")) {
            WalletClient client = new WalletClient();

            // Default: demo on alice
            if (args.length == 1) {
                client.demoCalls("alice");
                client.close();
                return;
            }

            // Transfer mode: client transfer <from> <to> <amount>
            if (args.length >= 2 && args[1].equalsIgnoreCase("transfer")) {
                if (args.length != 5) {
                    System.out.println("Usage: client transfer <fromAccount> <toAccount> <amount>");
                    client.close();
                    return;
                }

                String from = args[2].trim();
                String to = args[3].trim();
                double amount = Double.parseDouble(args[4]);

                client.transfer(from, to, amount);
                client.close();
                return;
            }

            // Otherwise: client <accountId>  (demo calls for that account)
            String accountId = args[1].trim();
            client.demoCalls(accountId);
            client.close();
            return;
        }

        System.out.println("Unknown mode: " + args[0]);
        printUsage();
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  server <port> <replicaId> <partitionId>");
        System.out.println("  client [accountId]");
        System.out.println("  client transfer <fromAccount> <toAccount> <amount>");
    }
}
