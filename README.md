# Postman
A Lightweight, Android compatible, Java 8, Rx based, web scale (ok we are taking the piss), non blocking pub/sub server with classes that can be used for discovery of services reliably
(I am looking at you NSD discovery on Android). Built in support for protobuf as to simplify the way communication happens with minimal overhead.
What more do you need?

If you are developing an application that requires communication over a local network with reliability and high performance, than 
postman might fit your needs. My current production use case is for a collaborative education application
that is communicates over a local network however there is no reaosn why this will not work over the internet either.

#### Key thoughts that have gone into this project

- Reactive interfaces. Because you know, its the rage now. I guess it's also a pretty decent library. Making use of RxJava and the benefits/simplicity it brings, all events and communication between client and server
are done over reactive components. 
- For simplicity purposes, all events happen over a single threaded event loop (For now). 
- The main message abstraction is a PostmanMessage, it has built in support for protobuf objects
- Solid test suite 
- After realising that the built in NSD discovery service for Android is extremely unreliable, Postman includes both bluetooth and Bonjour 
based discovery classes which are also RxJava based. We make use of the popular JmDNS library as well as a custom Bluetooth based discovery
component. 
- Java NIO based implementation for performance and non blocking benefits.


#### Exmaple usage

Start the server with the provided NIO non blocking server

```
 PostmanServer postmanServer = new NIOPostmanServer();
                
 postmanServer.serverStart(new InetSocketAddress("127.0.0.1", 12345))
                .observeOn(Schedulers.computation())
                .subscribe(postmanServerEvent -> {
                    //Do something with event
                    Connection clientConnection = postmanServerEvent.connection();
                    ....
                }, error -> {
                    //Something bad happened
                }, () -> {
                    //Server shutdown 
                });

```

Connect a client with the provided NIO based client

```
 PostmanClient postmanClient = new NIOPostmanClient();
 
  postmanClient.connect(provider.openSocketChannel(), InetAddress.getByName("127.0.0.1"), 12345)
                 .observeOn(Schedulers.computation())
                 .subscribe(postmanClientEvent -> {
                    Connection client = postmanClientEvent.connection();
                    //Do something with event/client connection
                 },
                 error -> {
                    //Something bad happened
                 }, () -> {
                    //Client shutdown
                 });

```

Pretty simple eh? I hear you asking, so how do we send messages?

```
postmanServer.sendMessage(new PostmanMessage(protobufMessage), connectionToSendTo);
//alternatively you can use the Connection object directory
connection.queueMessagetoSend(postmanMessage);
```

To send from the client to the server

```
posmtanClient.sendMessage(new PostmanMessage(protobufMessage));

```

Ok...This seems pretty easy. How about discovery?

```
//Start a service broadcast
PostmanDiscoveryService bonjourDiscoveryService = new BonjourDiscoveryService();
bounjourDiscoveryService.startServiceBroadcast(serviceType, serviceName, int port, addressToBroadcastOn)
    .observeOn(computationScheduler)
    .subscribe(broadcastEvent -> {
                  //Broadcast started 
                },
                error -> {
                   //Something bad happened
                }, () -> {
                   //broadcast stopped
                });
               
//To discovery the service

bounjourDiscoveryService.discoverService(serviceType, serviceName, addressToSearchOn)
    .observeOn(computationScheduler)
    .subscribe(discoveryEvent -> {
                  //Extract the discovered service info 
                },
                error -> {
                   //Something bad happened
                }, () -> {
                   //Discovery stopped
                });
                
                
```

There is also a Bluetooth based discovery component that uses the device name to discover a service.

#### Upcoming features/TODOs

- Out of order messaging support
- Upload initial version to a repo
- Performance improvements when dealing with large messages
