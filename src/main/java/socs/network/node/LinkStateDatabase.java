package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

import java.util.*;

public class LinkStateDatabase {

  //linkID => LSAInstance
  HashMap<String, LSA> _store = new HashMap<String, LSA>();

  private RouterDescription rd = null;

  public LinkStateDatabase(RouterDescription routerDescription) {
    rd = routerDescription;
    LSA l = initLinkStateDatabase();
    _store.put(l.linkStateID, l);
  }

  /**
   * output the shortest path from this router to the destination with the given IP address
   */
  String getShortestPath(String destinationIP) {

      //initialize all the required data structure for djikstra's algorithm
      HashSet<LSA> confirmed = new HashSet<LSA>();
      HashSet<LSA> tentative = new HashSet<LSA>();
      HashMap<LSA, Integer> distance = new HashMap<LSA, Integer>();
      HashMap<LSA, LSA> predecessors = new HashMap<LSA, LSA>();

      //get the source LSA and add it to the distance and tentative list
      LSA source = this._store.get(rd.simulatedIPAddress);
      distance.put(source, 0);
      tentative.add(source);

      //while there are remaining unevaluated nodes
      while (tentative.size() > 0) {
          LSA node = getMinimum(tentative, distance);
          confirmed.add(node);
          tentative.remove(node);
          findMinimalDistances(node, predecessors, distance, tentative);
      }
      System.out.println("confirmed" + confirmed);
      System.out.println("distance" + distance);
      System.out.println("predecessors: " + predecessors);

      LinkedList<LSA> path = getPath(this._store.get(destinationIP), predecessors);
      System.out.print("path" + path);

      return path.toString();

  }

    private void findMinimalDistances(LSA node, HashMap<LSA, LSA> predecessors, HashMap<LSA, Integer> distance, HashSet<LSA> tentative) {
        LinkedList<LSA> adjacentNodes = getNeighbors(node);
        System.out.println("adjacentNodes: " + adjacentNodes);
        for (LSA target : adjacentNodes) {
            System.out.println("target: " + target);
            if (getShortestDistance(target, distance) > getShortestDistance(node, distance)
                    + getDistance(node, target)) {
                distance.put(target, getShortestDistance(node, distance)
                        + getDistance(node, target));
                predecessors.put(target, node);
                tentative.add(target);
            }
        }

    }

    private int getDistance(LSA node, LSA target) {
        for (LinkDescription link : node.links) {
            System.err.println("link id: " + link.linkID);
            System.err.println("linkstate id: " + target.linkStateID);

            if (link.linkID.equals(target.linkStateID)) {
                System.out.println("metric: " + link.tosMetrics);
                return link.tosMetrics;
            }
        }
        throw new RuntimeException("Should not happen");
    }

    private LinkedList<LSA> getNeighbors(LSA node) {
        LinkedList<LSA> neighbors = new LinkedList<LSA>();
        System.out.println("node: " + node.toString());
        System.out.println("node.links: " + node.links);
        for (LinkDescription link : node.links) {
            System.out.println("linkid: " + link.linkID);
            LSA temp = _store.get(link.linkID);
            System.out.println("temp: " + temp.toString());
            neighbors.add(temp);
        }
        return neighbors;
    }

    //finds the link with the minimal distance
    private LSA getMinimum(Set<LSA> tentative, HashMap<LSA, Integer> distance) {
        LSA minimum = null;

        //for all routers in the tenative list..
        for (LSA router : tentative) {

            //if not minimum has been set yet then set first router to minimum
            minimum = (minimum == null || getShortestDistance(router, distance) <
                    getShortestDistance(minimum, distance)) ? router : minimum;

//            if (minimum == null) {
//                minimum = router;
//
//            //otherwise compare shortest distances to current minimum
//            } else {
//                if (getShortestDistance(router, distance) < getShortestDistance(minimum, distance)) {
//                    minimum = router;
//                }
//            }
        }
        return minimum;
    }

    private boolean isSettled(LSA vertex, HashSet<LSA> confirmed) {
        return confirmed.contains(vertex);
    }

    private int getShortestDistance(LSA destination, HashMap<LSA, Integer> distance) {
//        Integer d = distance.get(destination);

        return (distance.get(destination) == null) ? Integer.MAX_VALUE : distance.get(destination);
//        if (d == null) {
//            return Integer.MAX_VALUE;
//        } else {
//            return d;
//        }
    }

    /*
     * This method returns the path from the source to the selected target and
     * NULL if no path exists
     */
    public LinkedList<LSA> getPath(LSA target, HashMap<LSA, LSA> predecessors) {
        LinkedList<LSA> path = new LinkedList<LSA>();
        LSA step = target;
        // check if a path exists
        if (predecessors.get(step) == null) {
            return null;
        }
        path.add(step);
        while (predecessors.get(step) != null) {
            step = predecessors.get(step);
            path.add(step);
        }
        // Put it into the correct order
        Collections.reverse(path);


        return path;
    }


  //initialize the linkstate database by adding an entry about the router itself
  private LSA initLinkStateDatabase() {
    LSA lsa = new LSA();
    lsa.linkStateID = rd.simulatedIPAddress;
    lsa.lsaSeqNumber = Integer.MIN_VALUE;
    LinkDescription ld = new LinkDescription();
    ld.linkID = rd.simulatedIPAddress;
    ld.portNum = -1;
    ld.tosMetrics = 0;
    lsa.links.add(ld);
    return lsa;
  }


  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (LSA lsa: _store.values()) {
      sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
      for (LinkDescription ld : lsa.links) {
        sb.append(ld.linkID).append(",").append(ld.portNum).append(",").
                append(ld.tosMetrics).append("\t");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

}
