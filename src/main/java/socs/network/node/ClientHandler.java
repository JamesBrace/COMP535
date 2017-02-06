package socs.network.node;

import socs.network.message.SOSPFPacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

class ClientHandler implements Runnable {

    private Socket listener;
    private Router router;

    public ClientHandler(Socket listener, Router router) {
        this.listener = listener;
        this.router = router;
    }

    public void run() {
        try {

            ObjectInputStream inStream = new ObjectInputStream(listener.getInputStream());
            ObjectOutputStream outStream = new ObjectOutputStream(listener.getOutputStream());

            try {

                Object request = inStream.readObject();

                if (request != null) {

                    // check to see if received request is a string (this only happens with attach) and handle it accordingly
                    if (request instanceof String) {
                        String request_new = (String) request;

                        // the incoming request is for attachment, respond with ok
                        if (request_new.equals("Attempting to attach.")) {
                            outStream.writeObject("Ok.");
                        }
                    } else {
                        SOSPFPacket request_new = (SOSPFPacket) request;

                        //Received a HELLO!
                        if (request_new.sospfType == 0) {

                            //check to see if all ports are currently full and respond accordingly
                            int j;
                            for (j = 0; router.ports[j] != null; j++) {
                                if (j == 3) {
                                    outStream.writeObject("Error: All ports on the requested router are busy!");
                                    // clean up
                                    Router.cleanUp(outStream, inStream, listener);
                                    return;
                                }
                            }

                            System.out.println("received HELLO from " + request_new.srcIP + ";");

                            // check to make sure link doesnt already exist so that you dont add duplicates
                            String tempIP = request_new.srcIP;
                            boolean exists = false;

                            for (int i = 0; i < 4; i++) {
                                if (router.ports[i] != null && router.ports[i].router2.simulatedIPAddress.equals(tempIP)) {
                                    exists = true;
                                }
                            }

                            int port = -1;

                            if (!exists) {
                                RouterDescription router2 = new RouterDescription();
                                router2.processIPAddress = request_new.srcProcessIP;
                                router2.simulatedIPAddress = request_new.srcIP;
                                router2.processPortNumber = request_new.srcProcessPort;
                                router.ports[j] = new Link(router.rd, router2);
                                port = j;
                            } else {
                                //Need to identify which link this is coming from
                                for (int i = 0; i < 4; i++) {
                                    if (router.ports[i] != null && router.ports[i].router2.simulatedIPAddress.equals(request_new.srcIP)) {
                                        port = i;
                                        break;
                                    }
                                }
                            }

                            //Change the status of the client router to INIT
                            router.ports[port].router2.status = RouterStatus.INIT;

                            System.out.println("set " + request_new.srcIP + " state to INIT");

                            //Create outgoing packet
                            SOSPFPacket packet = new SOSPFPacket();

                            //set the data for the packet
                            packet.srcProcessIP = router.rd.processIPAddress;
                            packet.srcProcessPort = router.rd.processPortNumber;
                            packet.srcIP = router.rd.simulatedIPAddress;
                            packet.dstIP = router.ports[port].router2.simulatedIPAddress;
                            packet.sospfType = 0;
                            //figure this one out later
                            packet.routerID = "";
                            packet.neighborID = packet.srcIP;

                            outStream.writeObject(packet);

                            //Wait for response
                            request_new = (SOSPFPacket) inStream.readObject();

                            //check to make sure the packet received was a HELLO
                            if (request_new == null || request_new.sospfType != 0) {
                                System.out.println("Error: did not receive a HELLO back!");

                                // clean up
                                Router.cleanUp(outStream, inStream, listener);
                                return;
                            }

                            System.out.println("received HELLO from " + request_new.srcIP + ";");

                            router.ports[port].router2.status = RouterStatus.TWO_WAY;

                            System.out.println("set " + request_new.srcIP + " state to TWO_WAY");

                            System.out.print(">>");

                            // clean up
                            Router.cleanUp(outStream, inStream, listener);

                        }
                        //If packet is a LSA Update
                        else if (request_new.sospfType == 1) {
                            //This will handled in the next part

                        } else {
                            System.out.println("Error: Undefined Packet!");
                        }
                    }

                } else {
                    System.out.println("Error: Empty Packet!");
                }

            } catch (ClassNotFoundException ex) {
                System.err.println("Corrupted packet");
            }

        } catch (IOException ex) {
            System.err.println("Had trouble with Client Handler IO connection");
        }

    }
}
