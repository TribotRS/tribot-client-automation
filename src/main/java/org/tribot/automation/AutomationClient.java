package org.tribot.automation;

import java.util.List;
import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents a client connected to the automation server
 */
public interface AutomationClient extends AutoCloseable {

    /**
     * Closes the client
     */
    @Override
    void close();

    /**
     * Checks if this automation client is still connected
     *
     * @return true if it is connected, false otherwise
     */
    boolean isRunning();

    /**
     * Sets a runnable to run when this client triggers a ban event
     *
     * @param runnable the runnable to run
     */
    void onBan(Runnable runnable);

    /**
     * Sets a runnable to run when this client triggers a script end event
     *
     * @param runnable the runnable to run
     */
    void onScriptEnd(Runnable runnable);

    /**
     * Sets a runnable to run when this client disconnects
     *
     * @param runnable the runnable to run
     */
    void onDisconnect(Runnable runnable);

    /**
     * Sets a runnable to run when this client reconnects (connects after previously disconnecting)
     *
     * @param runnable the runnable to run
     */
    void onReconnect(Runnable runnable);

    /**
     * Sets a consumer to run when this client triggers a script message event
     *
     * @param consumer the consumer to run - it will accept the message sent by the script
     */
    void onScriptMessage(Consumer<String> consumer);

    /**
     * Sets a request handler to run when this client sends a script request
     *
     * @param requestHandler the request handler - it will take in the script request, and return an object
     *                       to serialize to json, which will be sent to the script
     */
    void onScriptRequest(Function<String, Object> requestHandler);

    /**
     * Sends a message to the script
     *
     * @param message the message to send to the script
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    void sendScriptMessage(String message) throws AutomationException;

    /**
     * Sends a request to the script to handle
     *
     * @param request the request to send
     * @param returnType the return type - the response from the script will be deserialized via json into this format
     * @param <T> the return type
     * @return the response from the server
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no
     * response, or if the client returns an RpcError
     */
    <T> T sendScriptRequest(String request, Class<T> returnType) throws AutomationException;

    /**
     * Sends a start script request to the client
     *
     * @param startScriptRequest the start script request
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    void startScript(StartScriptRequest startScriptRequest) throws AutomationException;

    /**
     * Sends a stop script request to the client
     *
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    void stopScript() throws AutomationException;

    /**
     * Sends a pause script request to the client
     *
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    void pauseScript() throws AutomationException;

    /**
     * Sends a resume script request to the client
     *
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    void resumeScript() throws AutomationException;

    /**
     * Gets the currently active script name
     *
     * @return the script name, or null if no script is running
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    String getScriptName() throws AutomationException;

    /**
     * Gets the login name of the client
     *
     * @return the login name, or null if no login name
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    String getLoginName() throws AutomationException;

    /**
     * Gets a screenshot of the game client
     *
     * @return a screenshot of the game client
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    Image getScreenshot() throws AutomationException;

    /**
     * Gets the account username
     *
     * @return the account username, or null if not logged in yet
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    String getUsername() throws AutomationException;

    /**
     * Gets the skill level of the specified skill
     *
     * @param skill the skill to get the level of
     * @return the skill level, or -1 if not logged in yet
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    int getSkillLevel(Skill skill) throws AutomationException;

    /**
     * Gets all skill levels
     *
     * @return all skill levels, or an empty map if not logged in yet
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    Map<Skill, Integer> getAllSkillLevels() throws AutomationException;

    /**
     * Gets all inventory items
     *
     * @return all inventory items (empty if not logged in yet)
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    List<Item> getInventoryItems() throws AutomationException;

    /**
     * Gets the current in-game position of this client
     *
     * @return the in-game position of this client, or null if not logged in yet
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    Tile getTile() throws AutomationException;

    /**
     * Checks if this client is logged in
     *
     * @return true if this client is logged in, false otherwise
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    boolean isLoggedIn() throws AutomationException;

    /**
     * Gets the current world of this client
     *
     * @return the current world of this client
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    int getWorld() throws AutomationException;

    /**
     * Gets the specified game setting value
     *
     * @param index the game setting index
     * @return the game setting at the specified index
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    int getGameSetting(int index) throws AutomationException;

    /**
     * Gets the specified varbit value
     *
     * @param index the varbit index
     * @return the varbit at the specified index
     * @throws AutomationException if there is an issue sending this request, ex. client disconnected, or no response
     */
    int getVarbit(int index) throws AutomationException;

}
