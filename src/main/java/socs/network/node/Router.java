package socs.network.node;

import org.jetbrains.annotations.Contract;
import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.TimeUnit;


public class Router {

    LinkStateDatabase lsd;

    RouterDescription rd = new RouterDescription();
    //assuming that all routers are with 4 ports
    Link[] ports = new Link[4];
    //flag that determines whether start has been called or not
    private boolean hasStarted = false;

    public Router(Configuration config) {

        //grab the necessary information from the config files
        rd.simulatedIPAddress = config.getString("socs.network.router.ip");
        rd.processPortNumber = config.getShort("socs.network.router.port");

        //assign the process IP to 127.0.0.1
        try {
            rd.processIPAddress = java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            System.err.println("Host IP does not exist");
        }

        //initialize LSD
        lsd = new LinkStateDatabase(rd);
    }


    /**
     * HELPER FUNCTIONS
     */

    //Checks if all the ports are empty
    @Contract(pure = true)
    private static boolean isEmpty(Link[] ports) {
        for (Link port : ports) {
            if (port != null) {
                return false;
            }
        }
        return true;
    }

    //Closes a sockets and all of its associated streams
    static void cleanUp(ObjectOutputStream output, ObjectInputStream input,
                        Socket clientSocket) throws IOException {

        // close the output stream
        if(output != null) {
            output.close();
        }

        // close the input stream
        if (input != null) {
            input.close();
        }
        // close the socket
        clientSocket.close();
    }

    //Checks if all the ports are empty
    @Contract(pure = true)
    private boolean isConnected() {
        for (Link port : this.ports) {
            if (port != null && port.router1.status == RouterStatus.TWO_WAY) {
                return true;
            }
        }
        return false;
    }

    //Returns the first free port
    int freePort() {
        //make sure ports are available
        for (int i = 0; i < 4; i++) {
            if (ports[i] == null) {
                return i;
            }
        }
        return -1;
    }

    //Constructs LSA for current router
    private LSA constructLSA() {
        LSA temp = new LSA();
        temp.linkStateID = this.rd.simulatedIPAddress;

        //check if this is the first LSA being sent and if so set seq number to 0
        if (lsd._store.get(this.rd.simulatedIPAddress).lsaSeqNumber == Integer.MIN_VALUE) {

            temp.lsaSeqNumber = 0;

            //otherwise increment current # by 1
        } else {

            int latest = lsd._store.get(this.rd.simulatedIPAddress).lsaSeqNumber;
            temp.lsaSeqNumber = latest + 1;
        }

        //grab all the links from the ports and create link descriptions from them
        temp.links = this.extractLinks();
        return temp;
    }

    //Looks at all the occupied ports and creates link descriptions for each link
    LinkedList<LinkDescription> extractLinks() {

        LinkedList<LinkDescription> links = new LinkedList<LinkDescription>();

        for (int i = 0; i < 4; i++) {
            if (ports[i] != null && ports[i].router2.status != null) {

                LinkDescription ld = new LinkDescription();
                ld.linkID = ports[i].router2.simulatedIPAddress;
                ld.portNum = ports[i].router2.processPortNumber;
                ld.tosMetrics = ports[i].weight;
                links.add(ld);

            }
        }

        return links;
    }

    //Returns weight of desired link
    private int getWeight(String dest) {
        for (int i = 0; i < 4; i++) {
            if (this.ports[i] != null && this.ports[i].router2.simulatedIPAddress.equals(dest)) {
                return this.ports[i].weight;
            }
        }
        System.err.println("Specified IP does not exist.");
        return -1;
    }


    //Returns weight of desired link
    public int getPort(String dest) {
        for (int i = 0; i < 4; i++) {
            if (this.ports[i] != null && this.ports[i].router2.simulatedIPAddress.equals(dest)) {
                return i;
            }
        }
        System.err.println("Specified IP does not exist.");
        return -1;
    }

    /**
     * My generic function for constructing packets. It has the ability to receive an IP address and an LSA as parameters
     * that notes that the packet is being constructed for an LSAUPDATE broadcast. Otherwise its a HELLO packet
     *
     * @param dest String of receiving IP address
     * @param lsa  LSA of current router
     */
    SOSPFPacket constructPacket(String dest, LSA lsa, short type) {

        SOSPFPacket packet = new SOSPFPacket();

        //set the data for the packet
        packet.srcProcessIP = this.rd.processIPAddress;
        packet.srcProcessPort = this.rd.processPortNumber;
        packet.srcIP = this.rd.simulatedIPAddress;
        packet.dstIP = dest;
        packet.sospfType = type;

        //figure this one out later
        packet.routerID = "";
        packet.neighborID = packet.srcIP;

        if (type == 0 || type == 2) {

            packet.HelloWeight = getWeight(dest);

        } else if (type == 1) {

            //create a temp vector and add LSA to it
            Vector<LSA> links = new Vector<LSA>();
            links.add(lsa);

            packet.lsaArray = links;

        }

        return packet;
    }

    /**
     * Either receives nothing and is in charge of creating new LSA and sending LSAUPDATE to all neighbors
     * or is simply just forwarding a recently received LSAUPDATE. Both cases it updates its current LSA Database
     *
     * @param forwardPacket SOSPFPacket
     * @param IP_Ignore     String
     * @throws IOException e
     */
    void broadcastUpdate(SOSPFPacket forwardPacket, String IP_Ignore) throws IOException {

        //check to make sure you are creating a LSAUPDATE instead of forwarding
        if (forwardPacket == null) {
            LSA lsa = constructLSA();

            //update LinkStateDatabase
            lsd._store.put(lsa.linkStateID, lsa);

            //send LSAUPDATE through all non-null ports
            for (int i = 0; i < 4; i++) {
                if (ports[i] != null) {

                    Socket clientSocket = new Socket(ports[i].router2.processIPAddress, ports[i].router2.processPortNumber);

                    ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());

                    //construct LSAUPDATE to broadcast across all ports
                    SOSPFPacket LSAUPDATE = constructPacket(this.ports[i].router2.simulatedIPAddress, lsa, (short) 1);

                    //broadcast the LSAUPDATE packet
                    output.writeObject(LSAUPDATE);

                    cleanUp(output, null, clientSocket);
                }
            }
            //otherwise you are forwarding a packet
        } else {

            //forward packet to all non-null ports
            for (int i = 0; i < 4; i++) {
                if (ports[i] != null) {

                    Socket clientSocket = new Socket(ports[i].router2.processIPAddress, ports[i].router2.processPortNumber);

                    ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());

                    //broadcast the LSAUPDATE packet
                    output.writeObject(forwardPacket);

                    cleanUp(output, null, clientSocket);
                }
            }
        }
    }

    //creates the link between host router and remote router
    private void createAttachment(String processIP, short processPort, String simulatedIP, short weight, RouterDescription remote, int free) {

        // attempt to connect with desired router
        try {

            Socket clientSocket = new Socket(processIP, processPort);

            ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());

            // make sure the router is "connectable"
            output.writeObject(simulatedIP);

            try {
                String incoming = (String) input.readObject();
                if (incoming.equals("Ok.")) {

                    // if all goes well, assign the new router link to the available port
                    ports[free] = new Link(rd, remote, weight);
                    cleanUp(output, input, clientSocket);
                } else {
                    System.err.println(incoming);
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (NullPointerException e) {
                throw new RuntimeException(e);
            }

        } catch (UnknownHostException e) {
            System.err.println("Don't know about host ");
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection");
        } catch (NullPointerException e) {
            System.err.println("Requested address is null");
        } catch (IllegalArgumentException e) {
            System.err.println("The port parameter is outside the specified range of valid port values, which is between 0 and 65535, inclusive");
        }
    }

    //creates the link between host router and remote router
    private boolean requestLinkDeletion(String processIP, short processPort, String simulatedIP) {

        // attempt to connect with desired router
        try {

            Socket clientSocket = new Socket(processIP, processPort);

            ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());

            //create packet that contains the deletion request header
            SOSPFPacket request = constructPacket(simulatedIP, null, (short) 3);

            //send the deletion request to the remote router
            output.writeObject(request);

            try {
                SOSPFPacket incoming = (SOSPFPacket) input.readObject();
                if (incoming.sospfType == 3) {

                    cleanUp(output, input, clientSocket);
                    return true;

                } else {
                    return false;
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (NullPointerException e) {
                throw new RuntimeException(e);
            }

        } catch (UnknownHostException e) {
            System.err.println("Don't know about host ");
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection");
        } catch (NullPointerException e) {
            System.err.println("Requested address is null");
        } catch (IllegalArgumentException e) {
            System.err.println("The port parameter is outside the specified range of valid port values, which is between 0 and 65535, inclusive");
        }

        return false;
    }


    /**
     * output the shortest path to the given destination ip
     * <p/>
     * format: source ip address  -> ip address -> ... -> destination ip
     *
     * @param destinationIP the ip address of the destination simulated router
     */
    private void processDetect(String destinationIP) {

        String path = this.lsd.getShortestPath(destinationIP);
        System.out.println(path);
    }

    /**
     * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {

      //check to make sure the port number is valid, that it is not null, and that there actually exists a two-way link
      if (portNumber > 3 || portNumber < 0 || ports[portNumber] == null || ports[portNumber].router2.status != RouterStatus.TWO_WAY) {
          System.err.println("Invalid port error.");
          return;
      }

      RouterDescription rd = ports[portNumber].router2;

      //get in touch with this router so you can send link deletion request
      if (requestLinkDeletion(rd.processIPAddress, rd.processPortNumber, rd.simulatedIPAddress)) {
          ports[portNumber] = null;

          try {
              broadcastUpdate(null, null);
          } catch (IOException e) {
              e.printStackTrace();
          }
      } else {
          System.err.println("Error while trying to delete link.");
      }
  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to identify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * NOTE: this command should not trigger link database synchronization
   * @throws IOException
   */
  private void processAttach(String processIP, short processPort, String simulatedIP, short weight) throws IOException {

      // setup RouterDescription for the desired router
      RouterDescription remote = new RouterDescription(processIP, processPort, simulatedIP);

      //check to make sure you aren't attaching to yourself
      if (simulatedIP.equals(this.rd.simulatedIPAddress)) {
          System.err.println("You can't attach to yourself!");
          return;
      }

      //check to make sure isn't already attached to requested remote router
      for (int x = 0; x < 4; x++) {
          if (ports[x] != null && ports[x].router2.simulatedIPAddress.equals(simulatedIP)) {
              System.err.println("You are already attached to this router!");
              return;
          }
      }

      // find first available port
      int free = freePort();

      //this means there are no available ports on the current router, return the appropriate message
      if (free == -1) {
          System.err.println("No more ports available!");
          return;
      }

      createAttachment(processIP, processPort, simulatedIP, weight, remote, free);
  }


    /**
   * broadcast Hello to neighbors
   */
  private void processStart() {
      //Make sure that current router is attached to at least one other router
      if (isEmpty(ports)) {
          System.err.println("You have started, but aren't connected to any routers.");
      }

      hasStarted = true;

      Socket clientSocket = null;
      ObjectOutputStream output = null;
      ObjectInputStream input = null;

      //Attempt to send a message to every port that current router is attached to
      for (int i = 0; i < ports.length; i++) {

          //if port is not being used, continue the loop
          if (ports[i] == null) continue;

          // Initialization section:
          // Try to open a socket on the given port
          // Try to open input and output streams
          String hostname = ports[i].router2.processIPAddress;
          short port = ports[i].router2.processPortNumber;

          try {
              clientSocket = new Socket(hostname, port);
              output = new ObjectOutputStream(clientSocket.getOutputStream());
              input = new ObjectInputStream(clientSocket.getInputStream());
          } catch (UnknownHostException e) {
              System.err.println("Don't know about host: " + hostname);
          } catch (IOException e) {
              System.err.println("Couldn't get I/O for the connection to: " + hostname);
          }

          // If everything has been initialized then we want to send a packet
          // to the socket we have opened a connection to on the given port
          try {
              SOSPFPacket packet = constructPacket(ports[i].router2.simulatedIPAddress, null, (short) 0);

              //broadcast the HELLO packet
              try {
                  output.writeObject(packet);
              } catch (NullPointerException e) {
                  cleanUp(output, input, clientSocket);
                  System.err.println("Trying to send a null packet!");
                  return;
              }

              //wait for response
              Object incoming_unk;

              try {
                  incoming_unk = input.readObject();
              } catch (ClassNotFoundException e) {
                  cleanUp(output, input, clientSocket);
                  System.err.println("Corrupted packet");
                  return;
              } catch (NullPointerException e) {
                  cleanUp(output, input, clientSocket);
                  System.err.println("Null packet");
                  return;
              }

              SOSPFPacket incoming;

              // this should only happen if receiving "Ports are full error". Use case ends
              if (incoming_unk instanceof String) {
                  String temp = (String) incoming_unk;

                  //need to delete current link from ports since it is impossible to have future conversation with this router
                  ports[i] = null;

                  System.out.println(temp + " Deleting link reference from port. Maybe try to attach again later.");
                  cleanUp(output, input, clientSocket);
                  continue;

                  //otherwise the HELLO packet was processed successfully
              } else {
                  incoming = (SOSPFPacket) incoming_unk;
              }

              //check to make sure the packet received was a HELLO
              if (incoming == null || incoming.sospfType != 0) {
                  System.out.println("Error: did not receive a HELLO back!");

                  // clean up
                  cleanUp(output, input, clientSocket);
                  return;
              }

              System.out.println("received HELLO from " + incoming.srcIP + ";");

              ports[i].router1.status = RouterStatus.TWO_WAY;
              ports[i].router2.status = RouterStatus.TWO_WAY;

              System.out.println("set " + incoming.srcIP + " state to TWO_WAY");

              //send back the HELLO packet
              output.writeObject(packet);

              //broadcast LSAUPDATE to neighbors
              broadcastUpdate(null, null);

              // clean up
              cleanUp(output, input, clientSocket);


          } catch (UnknownHostException e) {
              System.err.println("Trying to connect to unknown host: " + e);
          } catch (IOException e) {
              System.err.println("IOException:  " + e);
          }
      }
  }

    /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * This command does trigger the link database synchronization
   */
  private void processConnect(String processIP, short processPort,
                              String simulatedIP, short weight) {

      if (hasStarted) {

          //check to make sure you aren't connecting to yourself
          if (simulatedIP.equals(this.rd.simulatedIPAddress)) {
              System.err.println("You can't connect to yourself!");
              return;
          }

          //check to make sure isn't already connected to requested remote router
          for (int x = 0; x < 4; x++) {
              if (ports[x] != null && ports[x].router2.simulatedIPAddress.equals(simulatedIP)) {
                  System.err.println("You are already connected to this router!");
                  return;
              }
          }

          // setup RouterDescription for the desired router
          RouterDescription remote = new RouterDescription(processIP, processPort, simulatedIP);

          // find first available port
          int free = freePort();

          //this means there are no available ports on the current router, return the appropriate message
          if (free == -1) {
              System.err.println("No more ports available!");
              return;
          }

          // attempt to connect with desired router
          createAttachment(processIP, processPort, simulatedIP, weight, remote, free);

          //Basically do start except with the messaging back and forth
          Socket clientSocket = null;
          ObjectOutputStream output = null;
          ObjectInputStream input = null;


          // Initialization section:
          // Try to open a socket on the given port
          // Try to open input and output streams
          String hostname = ports[free].router2.processIPAddress;
          short port = ports[free].router2.processPortNumber;

          try {
              clientSocket = new Socket(hostname, port);
              output = new ObjectOutputStream(clientSocket.getOutputStream());
              input = new ObjectInputStream(clientSocket.getInputStream());
          } catch (UnknownHostException e) {
              System.err.println("Don't know about host: " + hostname);
          } catch (IOException e) {
              System.err.println("Couldn't get I/O for the connection to: " + hostname);
          }

          // If everything has been initialized then we want to send a packet
          // to the socket we have opened a connection to on the given port
          try {
              SOSPFPacket packet = constructPacket(ports[free].router2.simulatedIPAddress, null, (short) 2);

              //broadcast the HELLO packet
              try {
                  output.writeObject(packet);
              } catch (NullPointerException e) {
                  cleanUp(output, input, clientSocket);
                  System.err.println("Trying to send a null packet!");
                  return;
              }

              //wait for response
              Object incoming_unk;

              try {
                  incoming_unk = input.readObject();
              } catch (ClassNotFoundException e) {
                  cleanUp(output, input, clientSocket);
                  System.err.println("Corrupted packet");
                  return;
              } catch (NullPointerException e) {
                  cleanUp(output, input, clientSocket);
                  System.err.println("Null packet");
                  return;
              }

              SOSPFPacket incoming;

              // this should only happen if receiving "Ports are full error". Use case ends
              if (incoming_unk instanceof String) {
                  String temp = (String) incoming_unk;

                  //need to delete current link from ports since it is impossible to have future conversation with this router
                  ports[free] = null;

                  System.out.println(temp + " Deleting link reference from port. Maybe try to connect again later.");
                  cleanUp(output, input, clientSocket);
                  return;

                  //otherwise the HELLO packet was processed successfully
              } else {
                  incoming = (SOSPFPacket) incoming_unk;
              }

              //check to make sure the packet received was a HELLO
              if (incoming == null || incoming.sospfType != 2) {
                  System.out.println("Error: did not receive a CONNECT back!");

                  // clean up
                  cleanUp(output, input, clientSocket);
                  return;
              }


              ports[free].router1.status = RouterStatus.TWO_WAY;
              ports[free].router2.status = RouterStatus.TWO_WAY;


              //send back the HELLO packet
              output.writeObject(packet);

              //broadcast LSAUPDATE to neighbors
              broadcastUpdate(null, null);

              // clean up
              cleanUp(output, input, clientSocket);


          } catch (UnknownHostException e) {
              System.err.println("Trying to connect to unknown host: " + e);
          } catch (IOException e) {
              System.err.println("IOException:  " + e);
          }

      } else {
          System.err.println("You must run start command before attempting to connect!");
      }
  }


  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {
      boolean attached = true;
      if (isEmpty(ports)) {
          System.err.println("Ports are empty. No neighbors.");
      } else {
          for (int i = 0; i < ports.length; i++) {
              if (ports[i] != null && ports[i].router2.status != null) {
                  attached = false;
                  System.out.println("IP address of neighbor " + (i + 1) + ": " + ports[i].router2.simulatedIPAddress);
                  //System.out.println(ports[i].router2.simulatedIPAddress);
                  System.out.println("Weight: " + ports[i].weight);
              }
          }
          if (attached) {
              System.err.println("Ports are empty, but you have attached to other routers. Run start to create links.");
          }
      }
  }

    /**
     * disconnect with all neighbors and quit the program
     */
    private void processQuit() {

        //check to make sure you are connected to any remote routers
        if (!isConnected()) {
            System.out.println("Quitting process was successful.");
            System.exit(0);
        }

        //if you are connected to remote routers, then you need to disconnect from each one before exiting
        for (int i = 0; i < 4; i++) {

            //you can skip any null or non-started ports
            if (ports[i] == null || ports[i].router2.status != RouterStatus.TWO_WAY) continue;

            processDisconnect((short) i);

        }

        System.out.println("Quitting process was successful.");

        System.exit(0);

  }

  public void terminal() {
    try {
      InputStreamReader isReader = new InputStreamReader(System.in);
      BufferedReader br = new BufferedReader(isReader);
      System.out.print(">> ");
      String command = br.readLine();
      while (true) {
        if (command.startsWith("detect ")) {
          String[] cmdLine = command.split(" ");
          processDetect(cmdLine[1]);
        } else if (command.startsWith("disconnect ")) {
          String[] cmdLine = command.split(" ");
          processDisconnect(Short.parseShort(cmdLine[1]));
        } else if (command.startsWith("quit")) {
          processQuit();
            break;
        } else if (command.startsWith("attach ")) {
          String[] cmdLine = command.split(" ");
          processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("start")) {
          processStart();
        } else if (command.startsWith("connect ")) {
          String[] cmdLine = command.split(" ");
          processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("neighbors")) {
          //output neighbors
          processNeighbors();
        } else {
            System.out.print(">> ");
            command = br.readLine();
            continue;
        }

          TimeUnit.SECONDS.sleep(2);
          System.out.print(">> ");
        command = br.readLine();
      }
      isReader.close();
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


}
