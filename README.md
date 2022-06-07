## NOTE
This was intended to be released under TRiBot's github and through TRiBot's maven repo, but I had to step down from 
my developer role due to time constraints.

Things to be aware of:
* This works 99% - some things, like using the automation server to start a script on a client with no tab open will 
have tribot open a tab but tribot doesn't wait for the tab to open. Technically an issue in the TRiBot client that I 
was going to address but never got around to it.
* The tests aren't really automated tests atm. I was using them more as a way to easily manually test things. Could 
  easily be expanded on.
* This library probably won't be maintained moving forward since I am no longer a TRiBot 
  developer
* Otherwise, this is pretty cool. The CLI stuff is really nice (automatically downloading tribot, launching clients on 
  remote machines), and the automation stuff can be used as a reference of an implementation.


### TRiBot Client Automation Library
This combines the TRiBot CLI and automation server in an easy-to-use and simple java library.

### How To Use
Pull the library in through Maven or Gradle
< add the link here >

### Features
* Launch clients with TRiBot's CLI
* Automatically download and run tribot-splash (no configuration necessary)
* Run an automation server to connect to your clients
* All automation client methods
* All automation client callback events
* Launch clients on remote machines via SSH
* Configure all CLI args for full customization

### Automation Server
Create an automation server to connect to TRiBot's automation client to inspect and control your clients.

You can connect clients to this automation server in two ways:
1) Launch the clients with the appropriate CLI argument 
(ex. `--automation-url ws://127.0.0.1:8080/{id}`)

2) Launch the clients through this library's built-in launch support (see Client Launching below)

#### Example:
```java
final AutomationServer automationServer = AutomationServer.builder().build();

// Wait for clients to connect...

final Set<AutomationClient> allConnectedClients = automationServer.getClients();
// Do something with the clients
final List<String> allUsernames = allConnectedClients.stream()
        .map(AutomationClient::getUsername)
        .collect(Collectors.toList());

// Close it (when done) to kill the server
automationServer.close();
```


### Client Launching
Launch clients via TRiBot's CLI with full customization of all parameters.
#### Example:
```java
final Client client = Client.builder()
        .account(Account.builder()
                         .username("myusername")
                         .password("mypassword")
                        .build())
        .scriptName("nRestocker")
        .scriptArguments("settings:last")
        .build();
client.launch();
```

### Combined (Launch Clients for Server)
Launch clients that will connect to an active automation server.
#### Example:
````java
final AutomationServer automationServer = AutomationServer.builder().build();
final Client client = Client.builder()
        .account(Account.builder()
                         .username("myusername")
                         .password("mypassword")
                        .build())
        .scriptName("nRestocker")
        .scriptArguments("settings:last")
        .build();
final AutomationClient automationClient = client.launch(automationServer);
````


### SSH Support
* This supports connecting to a remote machine via SSH. 
* Configure SSH by specifying an `SshSettings` when building a `Client`
* Right now, there is a hard dependency on java being installed 
on this other machine. It can be any version 8+. It must be available via the `java` command. Tip: run `java 
  --version` to see what you have installed.

#### Example:
```java
final Client remoteClient = Client.builder()
        .account(Account.builder()
                        .username("test")
                        .password("asdf")
                        .build())
        .scriptName("nRestocker")
        .scriptArguments("settings:last")
        .sshSettings(SshSettings.builder()
                        .username("my-username")
                        .password("my-password")
                        .host("my-ssh-host")
                        .port(22)
                        .build())
        .build();
remoteClient.launch();
```

### Design Notes
#### Nothing happens asynchronously 
* Other than the websocket server accepting connections, all calls are blocking.
* Launching a client blocks until the client is launched. 
* Calling an automation client method blocks until a result is received. 

This is done intentionally to make it easy to use and reason about. The caller can run multiple tasks in 
parallel/handle concurrency however they would like. This will also allow the library to easily take advantage of Project Loom in
the future.

Note: these blocking situations do have appropriate timeouts and 
will throw an exception in that case.

Alternative async method variants could be added in the future if desired.