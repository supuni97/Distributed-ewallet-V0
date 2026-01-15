package uk.ac.westminster.ds;

import uk.ac.westminster.ds.client.WalletClient;
import uk.ac.westminster.ds.server.WalletServer;

public class Main {

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("  server <port>");
            System.out.println("  client");
            return;
        }

        if (args[0].equalsIgnoreCase("server")) {
            WalletServer.start(Integer.parseInt(args[1]));
        } else if (args[0].equalsIgnoreCase("client")) {
            WalletClient client = new WalletClient();
            client.demoCalls();
            client.close();
        }
    }
}
