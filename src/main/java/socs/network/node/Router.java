package socs.network.node;

import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
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
        output.close();
        input.close();
        clientSocket.close();
    }

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip adderss of the destination simulated router
   */
  private void processDetect(String destinationIP) {

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


//      if (!processIP.equals("localhost")) {
//          System.err.println("Sorry can only connect to localhost");
//          return;
//      }

      // find first available port
      int i;
      try {
          for (i = 0; ports[i] != null; i++) ;
      } catch (NullPointerException e) {
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
                  ports[i] = new Link(rd, remote);
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
              SOSPFPacket packet = new SOSPFPacket();

              //set the data for the packet
              packet.srcProcessIP = this.rd.processIPAddress;
              packet.srcProcessPort = this.rd.processPortNumber;
              packet.srcIP = this.rd.simulatedIPAddress;
              packet.dstIP = ports[i].router2.simulatedIPAddress;
              packet.sospfType = 0;
              //figure this one out later
              packet.routerID = "";
              packet.neighborID = packet.srcIP;

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

              System.out.println("set " + incoming.srcIP + "state to TWO_WAY");

              //broadcast the HELLO packet
              output.writeObject(packet);


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
      if (isEmpty(ports)) {
          System.out.println("Ports are empty. No neighbors.");
      } else {
          for (int i = 0; i < ports.length; i++) {
              if (ports[i] != null) {
                  System.out.println("IP address of neighbor " + (i + 1));
                  System.out.println(ports[i].router2.simulatedIPAddress);
              }
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
