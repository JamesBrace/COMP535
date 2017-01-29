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
                SOSPFPacket request = (SOSPFPacket) inStream.readObject();
                if (request != null) {

                    //Received a HELLO!
                    if (request.sospfType == 0) {
                        System.out.println("received HELLO from " + request.srcIP + ";");

                        //Need to identify which link this is coming from
                        int port = -1;

                        for (int i = 0; i < 4; i++) {
                            if (router.ports[i].router2.simulatedIPAddress.equals(request.srcIP)) {
                                port = i;
                                break;
                            }
                        }

                        //Change the status of the client router to INIT
                        router.ports[port].router2.status = RouterStatus.INIT;

                        System.out.println("set " + request.srcIP + "state to INIT");

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
                        request = (SOSPFPacket) inStream.readObject();

                        //check to make sure the packet received was a HELLO
                        if (request == null || request.sospfType != 0) {
                            System.out.println("Error: did not receive a HELLO back!");

                            // clean up
                            Router.cleanUp(outStream, inStream, listener);
                            return;
                        }

                        System.out.println("received HELLO from " + request.srcIP + ";");

                        router.ports[port].router2.status = RouterStatus.TWO_WAY;

                        System.out.println("set " + request.srcIP + "state to TWO_WAY");

                        // clean up
                        Router.cleanUp(outStream, inStream, listener);

                    }
                    //If packet is a LSA Update
                    else if (request.sospfType == 1) {
                        //This will handled in the next part

                    } else {
                        System.out.println("Error: Undefined Packet!");
                    }
                } else {
                    System.out.println("Error: Empty Packet!");
                }

            } catch (ClassNotFoundException ex) {
                System.err.println(new Throwable(ex));
            }

        } catch (IOException ex) {
            System.err.println(new Throwable(ex));
        }

    }
}
