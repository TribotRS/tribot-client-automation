package org.tribot.automation;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Represents an automation server for automation clients to connect to
 */
public interface AutomationServer extends AutoCloseable {

    /**
     * Gets all connected automation clients
     *
     * @return all connected automation clients
     */
    Set<AutomationClient> getClients();

    /**
     * Closes this server, disconnecting all clients and preventing new connections
     */
    @Override
    void close();

    /**
     * Creates a new automation server builder
     *
     * @return an automation server builder
     */
    static AutomationServerBuilder builder() {
        return AutomationServerImpl.builder();
    }

    /**
     * Represents a builder object to configure an automation server instance
     */
    interface AutomationServerBuilder {

        /**
         * The port to launch the automation server with.
         * Defaults to 8080.
         *
         * @param port the port to use
         * @return this builder
         */
        AutomationServerBuilder port(int port);

        /**
         * A consumer to run whenever a new client is connected
         *
         * @param consumer the client connection listener
         * @return this builder
         */
        AutomationServerBuilder onConnect(Consumer<AutomationClient> consumer);

        /**
         * Builds and launches the automation server. Clients will be able to connect.
         *
         * @return the built and started automation server
         * @throws AutomationException if there was an issue starting the automation server (ex. port in use)
         */
        AutomationServer build() throws AutomationException;

    }

}
