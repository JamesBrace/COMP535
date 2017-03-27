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
            //opens a socket that will constantly be looking for incoming requests
            ServerSocket socket = new ServerSocket(router.rd.processPortNumber);

            while (true) {

                // the server socket will listen to this port indefinitely
                Socket listener = socket.accept();

                // when an incoming request comes through, create a thread to handle the request
                Thread client = new Thread(new ClientHandler(listener, router));
                client.start();

            }
        } catch (IOException ex) {
            System.err.println("Error with server side of the router");
        }
    }
}
