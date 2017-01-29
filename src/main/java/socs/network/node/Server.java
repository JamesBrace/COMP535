package socs.network.node;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public class Server implements Runnable {
    private Router router;

    public Server(Router router) {
        this.router = router;
    }

    public void run() {
        try {
            while (true) {

                ServerSocket socket = new ServerSocket(5000); //will need to figure out how to configure port numbers properly
                Socket listener = socket.accept();

                Thread client = new Thread(new ClientHandler(listener, router));
                client.start();

            }
        } catch (IOException ex) {
            System.err.println(new Throwable(ex));
        }
    }
}
