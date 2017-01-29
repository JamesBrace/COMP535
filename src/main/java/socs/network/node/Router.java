package socs.network.node;

import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;


public class Router {

  protected LinkStateDatabase lsd;

  RouterDescription rd = new RouterDescription();

  //assuming that all routers are with 4 ports
  Link[] ports = new Link[4];

  public Router(Configuration config) {
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
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
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * NOTE: this command should not trigger link database synchronization
   */
  private void processAttach(String processIP, short processPort,
                             String simulatedIP, short weight) {

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
              output.writeObject(packet);

              //wait for response
              SOSPFPacket incoming;

              try {
                  incoming = (SOSPFPacket) input.readObject();
              } catch (ClassNotFoundException e) {
                  System.err.println(e);
                  return;
              } catch (NullPointerException e) {
                  System.err.println(e);
                  return;
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
