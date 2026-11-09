# Architecture Overview
Follows Two-Layered architecture

## Application Level:
Every instance of application (A particular device running this application) will talk directly to
the main server and which internally communicates with a database. Users generally don't interfere
directly with this level of server.

## User Level:
This level is for commuication among devices that user have opted in for access. The network of 
devices formed will communicate with each other through their own light-weight host and client 
modules, after all the required setup has been done.

This level is basically a Peer-to-Peer architecture, where every device acts as both server and
client, sharing information between them. So, no user specific data will be sent out of the network.

![UserLevelViz](../assets/images/user_level_viz.png)

## Workflow

### Sending requests to a device
```
plugins / services
    ↓
  cient
    ↓
Target Device
```

### Receving and attending requests
```
Target Device
    ↓
host (command router)
    ↓
plugins / services
```

## Communication Model
- module &harr; service || service &harr; service || module &harr; module
    - Sockets / IPC

- Device ↔ Device
    - Sockets (real-time communication)

- App ↔ Main Server
    - HTTP / HTTPS (authentication & metadata)

---