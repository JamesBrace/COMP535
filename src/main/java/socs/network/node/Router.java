package socs.network.node;

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

    protected LinkStateDatabase lsd;

    RouterDescription rd = new RouterDescription();

    //assuming that all routers are with 4 ports
    Link[] ports = new Link[4];

    public Router(Configuration config) {
        rd.simulatedIPAddress = config.getString("socs.network.router.ip");
        rd.processPortNumber = config.getShort("socs.network.router.port");

        try {
            rd.processIPAddress = java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            System.err.println("Host IP does not exist");
        }


        lsd = new LinkStateDatabase(rd);
    }


    /**
     * HELPER FUNCTIONS
     */

    public static boolean isEmpty(Link[] ports) {
        for (int i = 0; i < ports.length; i++) {
            if (ports[i] != null) {
                return false;
            }
        }
        return true;
    }

    public static void cleanUp(ObjectOutputStream output, ObjectInputStream input,
                               Socket clientSocket) throws IOException {
        // clean up:
        // close the output stream
        // close the input stream
        // close the socket
        if(output != null) {
            output.close();
        }
        if (input != null) {
            input.close();
        }
        clientSocket.close();
    }

    public int freePort() {
        //make sure ports are available
        for (int i = 0; i < 4; i++) {
            if (ports[i] == null) {
                return i;
            }
        }
        return -1;
    }

    public LSA constructLSA() {
        LSA temp = new LSA();
        temp.linkStateID = this.rd.simulatedIPAddress;

        //check if this is the first LSA being sent
        if (lsd._store.get(this.rd.simulatedIPAddress).lsaSeqNumber == Integer.MIN_VALUE) {
            temp.lsaSeqNumber = 0;

            //debug
            System.out.println("lsaSeqNumber: " + temp.lsaSeqNumber);
        } else {
            int latest = lsd._store.get(this.rd.simulatedIPAddress).lsaSeqNumber;
            temp.lsaSeqNumber = latest + 1;

            //debug
            System.out.println("lsaSeqNumber: " + temp.lsaSeqNumber);
        }

        //get all links from array
        LinkedList<LinkDescription> links = new LinkedList<LinkDescription>();

        for (int i = 0; i < 4; i++) {
            if (ports[i] != null) {
                if (ports[i].router2.status != null) {
                    //debug
                    System.out.println("Adding to links");
                    LinkDescription ld = new LinkDescription();
                    ld.linkID = ports[i].router2.simulatedIPAddress;
                    ld.portNum = ports[i].router2.processPortNumber;
                    ld.tosMetrics = ports[i].weight;
                    links.add(ld);
                }
            }
        }

        temp.links = links;
        return temp;
    }

    public SOSPFPacket constructPacket(String dest, LSA lsa) {
        SOSPFPacket packet = new SOSPFPacket();
        //set the data for the packet
        packet.srcProcessIP = this.rd.processIPAddress;
        packet.srcProcessPort = this.rd.processPortNumber;
        packet.srcIP = this.rd.simulatedIPAddress;
        packet.dstIP = dest; //ports[i].router2.simulatedIPAddress;

        //figure this one out later
        packet.routerID = "";
        packet.neighborID = packet.srcIP;

        //CONFUSED ON WHY THERE ARE MULTIPLE LSA'S
        if (lsa != null) {
            System.out.println(lsa);
            packet.sospfType = 1;

            //create a temp vector and add LSA to it
            Vector<LSA> links = new Vector<LSA>();
            links.add(lsa);

            packet.lsaArray = links;
        } else {
            packet.sospfType = 0;
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
    public void broadcastUpdate(SOSPFPacket forwardPacket, String IP_Ignore) throws IOException {
        //LSAUPDATE WILL HAPPEN HERE!
        if (forwardPacket == null) {
            LSA lsa = constructLSA();

            //update LinkStateDatabase
            lsd._store.put(lsa.linkStateID, lsa);

            for (int i = 0; i < 4; i++) {
                if (ports[i] != null) {

                    Socket clientSocket = new Socket(ports[i].router2.processIPAddress, ports[i].router2.processPortNumber);

                    ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());

                    SOSPFPacket LSAUPDATE = constructPacket(this.ports[i].router2.simulatedIPAddress, lsa);

                    //broadcast the LSAUPDATE packet
                    output.writeObject(LSAUPDATE);

                    cleanUp(output, null, clientSocket);
                }
            }
        } else {
            //update LinkStateDatabase
            LSA lsa = forwardPacket.lsaArray.lastElement();

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


    /**
     * output the shortest path to the given destination ip
     * <p/>
     * format: source ip address  -> ip address -> ... -> destination ip
     *
     * @param destinationIP the ip adderss of the destination simulated router
     */
    private void processDetect(String destinationIP) {
        this.lsd.getShortestPath(destinationIP);
    }

    /**
     * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {

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
      RouterDescription remote = new RouterDescription();
      remote.processIPAddress = processIP;
      remote.processPortNumber = processPort;
	  remote.simulatedIPAddress = simulatedIP;

	  //check to make sure isn't already attached to requested remote router
      for (int x = 0; x < 4; x++) {
          if (ports[x] != null) {
              if (ports[x].router2.simulatedIPAddress.equals(simulatedIP)) {
                  System.err.println("You are already attached to this router!");
                  return;
              }
          }
      }

      // find first available port
      int free = freePort();

      if (free == -1) {
          System.err.println("No more ports available!");
          return;
      }


      // attempt to connect with desired router
      try {

          Socket clientSocket = new Socket(processIP, processPort);

          ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());
          ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());

          // make sure the router is "connectable"
          output.writeObject("Attempting to attach.");

          try {
              String incoming = (String) input.readObject();
              if (incoming.equals("Ok.")) {
                  // if all goes well, assign the new router link to the available port
                  ports[free] = new Link(rd, remote, weight);
                  cleanUp(output, input, clientSocket);
              }
          } catch (ClassNotFoundException e) {
              System.err.println(e);
          } catch (NullPointerException e) {
              System.err.println(e);
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

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {
      //Make sure that current router is attached to at least one other router
      if (isEmpty(ports)) {
          System.err.println("You must attach to another router before starting.");
          return;
      }

      Socket clientSocket = null;
      ObjectOutputStream output = null;
      ObjectInputStream input = null;

      //Attempt to send a message to every port that current router is attached to
      for (int i = 0; i < ports.length; i++) {

          //if port is not being used, continue the loop
          if (ports[i] == null) {
              continue;
          }

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
              SOSPFPacket packet = constructPacket(ports[i].router2.simulatedIPAddress, null);

              //                //set the data for the packet
              //                packet.srcProcessIP = this.rd.processIPAddress;
              //                packet.srcProcessPort = this.rd.processPortNumber;
              //                packet.srcIP = this.rd.simulatedIPAddress;
              //                packet.dstIP = ports[i].router2.simulatedIPAddress;
              //                packet.sospfType = 0;
              //                //figure this one out later
              //                packet.routerID = "";
              //                packet.neighborID = packet.srcIP;

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
                  System.out.println(temp);
                  cleanUp(output, input, clientSocket);
                  return;
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

              System.out.println("set " + incoming.srcIP + "state to TWO_WAY");

              //send back the HELLO packet
              output.writeObject(packet);

              //broadcast LSAUPDATE to neighbors

              //debug
              System.out.println("About to send LSAUPDATEs");

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
                  System.out.println("IP address of neighbor " + (i + 1));
                  System.out.println(ports[i].router2.simulatedIPAddress);
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
        } else if (command.startsWith("attach ")) {
          String[] cmdLine = command.split(" ");
          processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("start")) {
          processStart();
        } else if (command.equals("connect ")) {
          String[] cmdLine = command.split(" ");
          processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("neighbors")) {
          //output neighbors
          processNeighbors();
        } else {
          //invalid command
          break;
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
