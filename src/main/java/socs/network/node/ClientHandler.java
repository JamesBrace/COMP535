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

            //open the input and output streams
            ObjectInputStream inStream = new ObjectInputStream(listener.getInputStream());
            ObjectOutputStream outStream = new ObjectOutputStream(listener.getOutputStream());

            try {

                //get incoming packet
                Object request = inStream.readObject();

                if (request != null) {

                    // check to see if received request is a string (this only happens with attach) and handle it accordingly
                    if (request instanceof String) {

                        String request_new = (String) request;


                        //check to make sure I entered right simulated IP else path finding will get screwed up!
                        if (request_new.equals(router.rd.simulatedIPAddress)) {
                            outStream.writeObject("Ok.");
                        } else {
                            outStream.writeObject("You seem to have entered the wrong simulated IP!");
                        }

                    } else {
                        SOSPFPacket request_new = (SOSPFPacket) request;

                        //Received a HELLO!
                        if (request_new.sospfType == 0) {

                            // check to make sure link doesnt already exist so that you dont add duplicates
                            String tempIP = request_new.srcIP;
                            boolean exists = false;

                            int port = -1;

                            for (int i = 0; i < 4; i++) {
                                if (router.ports[i] != null && router.ports[i].router2.simulatedIPAddress.equals(tempIP)) {
                                    exists = true;
                                    port = i;
                                    break;
                                }
                            }

                            //if the current link does not exist...
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
                                router.ports[free] = new Link(router.rd, router2, request_new.HelloWeight);
                                port = free;
                            }

                            System.out.println("received HELLO from " + request_new.srcIP + ";");

                            //Change the status of the client router to INIT
                            router.ports[port].router2.status = RouterStatus.INIT;
                            router.ports[port].router1.status = RouterStatus.INIT;

                            System.out.println("set " + request_new.srcIP + " state to INIT");

                            //Create outgoing packet
                            SOSPFPacket packet = router.constructPacket(router.ports[port].router2.simulatedIPAddress, null, (short) 0);

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
                            LSA lsa = router.lsd._store.get(request_new.srcIP);

                            boolean newRouter = false;

                            //if the incoming LSA is newer than current, then update database and propagate info
                            if (lsa == null || (request_new.lsaArray.lastElement().lsaSeqNumber > lsa.lsaSeqNumber)) {

                                newRouter = lsa == null;

                                //see if the link between this router and the sender exists (i.e. is a direct neighbor)
                                boolean linkExists = false;
                                int port = -1;

                                //compare IP address to the source's IP address
                                for (int i = 0; i < 4; i++) {
                                    if (router.ports[i] != null && router.ports[i].router2.simulatedIPAddress.equals(request_new.srcIP)) {
                                        linkExists = true;
                                        port = i;
                                        break;
                                    }
                                }

                                //if the link exists, you need to update link in port as well as your current LSA
                                if (linkExists) {

                                    LinkedList<LinkDescription> tempList = request_new.lsaArray.lastElement().links;
                                    LinkDescription link = null;

                                    //find the link description of current router in the LSA
                                    for (LinkDescription ld : tempList) {

                                        //if the link description matches the current router and it has a different weight
                                        if (ld.linkID.equals(router.rd.simulatedIPAddress)) {
                                            link = ld;
                                            break;
                                        }
                                    }

                                    //make sure both routers are actually aware of each other
                                    if (link != null) {
                                        //if the LSA is saying that there is an outdated weight, then update weight in port and LinkState
                                        if (link.tosMetrics != router.ports[port].weight && link.tosMetrics > -1) {

                                            //update link description in port
                                            router.ports[port].weight = link.tosMetrics;

                                            //get current LSA from LSD
                                            LSA current = router.lsd._store.get(router.rd.simulatedIPAddress);

                                            //get new link descriptions
                                            current.links = router.extractLinks();

                                            router.lsd._store.put(router.rd.simulatedIPAddress, current);

                                            router.broadcastUpdate(null, null);

                                        }
                                    }

                                }

                                router.lsd._store.put(request_new.srcIP, request_new.lsaArray.lastElement());


                                //forward LSAUPDATE to all neighbors
                                for (int i = 0; i < 4; i++) {
                                    if (router.ports[i] != null && !router.ports[i].router2.simulatedIPAddress.equals(request_new.srcIP)) {
                                        router.broadcastUpdate(request_new, request_new.srcIP);

                                        if (newRouter) {
                                            //if new router, you need to broadcast yourself so it can be aware of you
                                            router.broadcastUpdate(null, null);
                                        }
                                    }
                                }
                            }
                        }
                        //it's a disconnect request
                        else if (request_new.sospfType == 3) {

                            //create the response packet
                            SOSPFPacket response = router.constructPacket(request_new.srcIP, null, (short) 3);

                            //send the response to the source so it can update it's link state database
                            outStream.writeObject(response);

                            //proceed to update link state database
                            int port = router.getPort(request_new.srcIP);

                            router.ports[port] = null;

                            router.broadcastUpdate(null, null);


                        }
                        //otherwise it's a connection request
                        else {

                            // check to make sure link doesnt already exist so that you dont add duplicates
                            String tempIP = request_new.srcIP;
                            boolean exists = false;

                            int port = -1;

                            for (int i = 0; i < 4; i++) {
                                if (router.ports[i] != null && router.ports[i].router2.simulatedIPAddress.equals(tempIP)) {
                                    exists = true;
                                    port = i;
                                    break;
                                }
                            }

                            //if the current link does not exist...
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
                                RouterDescription router2 = new RouterDescription(request_new.srcProcessIP,
                                        request_new.srcProcessPort, request_new.srcIP);
                                router.ports[free] = new Link(router.rd, router2, request_new.HelloWeight);
                                port = free;
                            }


                            //Change the status of the client router to INIT
                            router.ports[port].router2.status = RouterStatus.INIT;
                            router.ports[port].router1.status = RouterStatus.INIT;

                            //Create outgoing packet
                            SOSPFPacket packet = router.constructPacket(router.ports[port].router2.simulatedIPAddress, null, (short) 2);

                            outStream.writeObject(packet);

                            //Wait for response
                            request_new = (SOSPFPacket) inStream.readObject();

                            //check to make sure the packet received was a HELLO
                            if (request_new == null || request_new.sospfType != 2) {

                                System.out.println("Error: did not receive a CONNECT back!");

                                // clean up
                                Router.cleanUp(outStream, inStream, listener);
                                return;
                            }

                            router.ports[port].router2.status = RouterStatus.TWO_WAY;
                            router.ports[port].router1.status = RouterStatus.TWO_WAY;

                            //broadcast LSAUPDATE to neighbors
                            router.broadcastUpdate(null, null);

                            // clean up
                            Router.cleanUp(outStream, inStream, listener);
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
