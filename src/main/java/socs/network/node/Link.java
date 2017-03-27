package socs.network.node;

class Link {

  RouterDescription router1;
  RouterDescription router2;
    int weight;

    Link(RouterDescription r1, RouterDescription r2, int weight) {
    router1 = r1;
    router2 = r2;
        this.weight = weight;
  }
}
