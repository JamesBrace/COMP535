package socs.network.node;

class RouterDescription {
    //used for socket communication
    String processIPAddress;
    short processPortNumber;

    //used to identify the router in the simulated network space
    String simulatedIPAddress;

    //status of the router
    RouterStatus status;

    RouterDescription(String processIPAddress, short processPortNumber, String simulatedIPAddress) {
        this.processIPAddress = processIPAddress;
        this.processPortNumber = processPortNumber;
        this.simulatedIPAddress = simulatedIPAddress;
    }

    RouterDescription() {
    }
}
