package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

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
                        outStream.writeObject("Ok.");

                    } else {
                        SOSPFPacket request_new = (SOSPFPacket) request;

                        //Received a HELLO!
                        if (request_new.sospfType == 0) {

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

                                //find next available port
                                int free = router.freePort();

                                //no available ports, return error
                                if (free == -1) {
                                    outStream.writeObject("Error: All ports on the requested router are busy!");
                                    // clean up
                                    Router.cleanUp(outStream, inStream, listener);
                                    return;
                                }

                                //otherwise, create the new link
                                RouterDescription router2 = new RouterDescription();
                                router2.processIPAddress = request_new.srcProcessIP;
                                router2.simulatedIPAddress = request_new.srcIP;
                                router2.processPortNumber = request_new.srcProcessPort;
                                router.ports[free] = new Link(router.rd, router2, -1);
                                port = free;

                                //otherwise, this link already exists and you need to not create a new link
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
                            router.ports[port].router1.status = RouterStatus.INIT;


                            System.out.println("set " + request_new.srcIP + " state to INIT");

                            //Create outgoing packet
                            SOSPFPacket packet = router.constructPacket(router.ports[port].router2.simulatedIPAddress, null);

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
                            router.ports[port].router1.status = RouterStatus.TWO_WAY;

                            System.out.println("set " + request_new.srcIP + " state to TWO_WAY");

                            //broadcast LSAUPDATE to neighbors
                            router.broadcastUpdate(null, null);

                            TimeUnit.SECONDS.sleep(2);
                            System.out.print(">> ");

                            // clean up
                            Router.cleanUp(outStream, inStream, listener);

                        }
                        //If packet is a LSA Update
                        else if (request_new.sospfType == 1) {

                            //check to see if sequence # is greater than current by getting the most recent LSA
                            //from the requested source IP
                            LSA temp = router.lsd._store.get(request_new.srcIP);

                            //if the incoming LSA is newer than current, then update database and propagate info
                            if (temp == null || (request_new.lsaArray.lastElement().lsaSeqNumber > temp.lsaSeqNumber)) {

                                //see if the link between this router and the sender exists, if so update current link weight
                                for (int i = 0; i < 4; i++) {
                                    if (router.ports[i] != null && router.ports[i].router2.simulatedIPAddress.equals(request_new.srcIP)) {

                                        LinkedList<LinkDescription> tempList = request_new.lsaArray.lastElement().links;

                                        for (LinkDescription ld : tempList) {
                                            if (ld.linkID.equals(router.rd.simulatedIPAddress) && ld.tosMetrics > router.ports[i].weight) {
                                                router.ports[i].weight = ld.tosMetrics;
                                            }
                                        }
                                    }
                                }

                                router.lsd._store.put(request_new.srcIP, request_new.lsaArray.lastElement());

                                //send out LSAUPDATE to all neighbors
                                for (int i = 0; i < 4; i++) {
                                    if (router.ports[i] != null && !router.ports[i].router2.simulatedIPAddress.equals(request_new.srcIP)) {
                                        router.broadcastUpdate(request_new, request_new.srcIP);
                                    }
                                }
                            }

                        } else {
                            System.out.println("Error: Undefined Packet!");
                        }
                    }

                } else {
                    System.out.println("Error: Empty Packet!");
                }

            } catch (ClassNotFoundException ex) {
                System.err.println("Corrupted packet");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } catch (IOException ex) {
            System.err.println("Had trouble with Client Handler IO connection");
        }

    }
}
