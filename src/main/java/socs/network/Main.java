package socs.network;

import socs.network.node.Router;
import socs.network.node.Server;
import socs.network.util.Configuration;

public class Main {

  public static void main(String[] args) {
    if (args.length != 1) {
      System.out.println("usage: program conf_path");
      System.exit(1);
    }
    //creates router that will perform all the actions
    Router r = new Router(new Configuration(args[0]));

    //have a separate entity accepting and handling incoming client requests
    Thread server = new Thread(new Server(r));
    server.start();

    System.out.println("starting terminal");

    r.terminal();

  }
}
