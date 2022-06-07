package org.tribot.automation;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
class AutomationServerImpl implements AutomationServer {

	private static final Gson gson = new Gson();

	private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

	private final Server server;

	private final Consumer<AutomationClient> onConnect;

	private final Map<String, WebSocket> clientConnections;
	private final Map<String, Future<?>> cleanTasks;
	// Client ID -> Event Class -> Event Listeners
	private final Map<String, Map<Class<? extends AutomationEvent>, List<Consumer<? extends AutomationEvent>>>> eventListeners;
	// Allows waiting to connect
	private final Map<String, WaitForConnectionContext> pendingConnections;

	private final Map<String, CompletableFuture<Response>> pendingMessages;

	@Getter(AccessLevel.PACKAGE)
	private final int port;

	@lombok.Builder
	AutomationServerImpl(int port, Consumer<AutomationClient> onConnect) throws AutomationException {
		this.port = port;
		clientConnections = new ConcurrentHashMap<>();
		pendingMessages = new ConcurrentHashMap<>();
		pendingConnections = new ConcurrentHashMap<>();
		eventListeners = new ConcurrentHashMap<>();
		cleanTasks = new ConcurrentHashMap<>();
		this.onConnect = onConnect;
		try {
			server = new Server(port);
			server.start();
			log.debug("Created automation server.");
			log.debug("Listening for local connections at: {} and remote connections at {}",
					getLocalConnectionUrl(), getRemoteConnectionUrl());
		}
		catch (Exception e) {
			throw new AutomationException(e);
		}
	}

	@Override
	public Set<AutomationClient> getClients() {
		return clientConnections.keySet()
				.stream()
				.map(id -> new AutomationClientImpl(this, id))
				.collect(Collectors.toUnmodifiableSet());
	}

	String getLocalConnectionUrl() {
		return "ws://127.0.0.1:" + getPort() + "/{id}";
	}

	String getRemoteConnectionUrl() {
		return "ws://" + getPublicIpAddress() + ":" + getPort() + "/{id}";
	}

	private String getPublicIpAddress() throws AutomationException {
		try {
			final URL whatismyip = new URL("http://checkip.amazonaws.com");
			final URLConnection connection = whatismyip.openConnection();
			connection.setConnectTimeout(15000);
			connection.setReadTimeout(15000);
			try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
				return in.readLine();
			}
		}
		catch (Exception e) {
			log.warn("Failed to get public IP", e);
			throw new AutomationException(e);
		}
	}

	boolean isConnected(String id) {
		return clientConnections.containsKey(id);
	}

	String getConnectionUrl(String clientId, boolean local) {
		final String url = local ? getLocalConnectionUrl() : getRemoteConnectionUrl();
		return url.replace("{id}", clientId);
	}

	void register(WaitForConnectionContext ctx) {
		this.pendingConnections.put(ctx.getId(), ctx);
	}

	void unregister(WaitForConnectionContext ctx) {
		this.pendingConnections.remove(ctx.getId());
	}

	<T extends AutomationEvent> void onEvent(String clientId, Class<T> automationEvent, Consumer<T> onEvent) {
		eventListeners.computeIfAbsent(clientId, i -> new ConcurrentHashMap<>())
				.computeIfAbsent(automationEvent, e -> new CopyOnWriteArrayList<>())
				.add(onEvent);
	}

	<T extends AutomationEvent> void sendEvent(String id, T automationEvent) {
		final var connectionEventListeners = eventListeners.get(id);
		if (connectionEventListeners == null) {
			return;
		}
		final var eventListeners = connectionEventListeners.get(automationEvent.getClass());
		if (eventListeners == null) {
			return;
		}
		eventListeners.forEach(listener -> {
			// Raw type on purpose, let the generics compile...
			try {
				((Consumer) listener).accept(automationEvent);
			}
			catch (Exception e) {
				log.error("Exception while processing event listener", e);
			}
		});
	}

	void clean(String id) {
		if (clientConnections.containsKey(id)) {
			return;
		}
		eventListeners.remove(id);
		pendingConnections.remove(id);
		cleanTasks.remove(id);
	}

	void sendResponse(String clientId, String messageId, Object response) {
		final WebSocket context = clientConnections.get(clientId);
		if (context == null) {
			return;
		}
		final Response res = new Response(messageId, gson.toJsonTree(response), null);
		final String text = gson.toJson(res);
		context.send(text);
	}

	void sendResponse(String clientId, String messageId, Throwable response) {
		final WebSocket context = clientConnections.get(clientId);
		if (context == null) {
			return;
		}
		final JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("message", response.getMessage());
		final RpcError error = new RpcError(500, "Exception while processing even handler", jsonObject);
		final Response res = new Response(messageId, null, error);
		final String text = gson.toJson(res);
		context.send(text);
	}

	<R> R send(String clientId, AutomationRequest<R> automationRequest) {
		log.debug("Attempting to send automation request for client (clientId={}): {}", clientId, automationRequest);
		String messageId = null;
		try {
			final WebSocket context = clientConnections.get(clientId);
			if (context == null) {
				throw new AutomationException("Client not connected");
			}
			final JsonElement params = automationRequest.getParams().isEmpty()
					? null
					: gson.toJsonTree(automationRequest.getParams());
			final Request message = Request.builder()
			                               .method(automationRequest.getMethod())
			                               .params(params)
			                               .build();
			messageId = message.getId();
			final String body = gson.toJson(message);
			context.send(body);
			final CompletableFuture<Response> completableFuture = new CompletableFuture<>();
			pendingMessages.put(message.getId(), completableFuture);
			final Response response = completableFuture.get(10, TimeUnit.SECONDS);
            log.debug("Received automation response: {}", response);
			if (response.error != null) {
				throw new AutomationException(response.error.toString());
			}
			Type returnType = automationRequest.returnType;
			if (returnType == null) {
				// Not expecting any return value
				return null;
			}
			if (!automationRequest.returnTypeGenericTypes.isEmpty()) {
				returnType = TypeToken.getParameterized(returnType,
						automationRequest.returnTypeGenericTypes.toArray(new Type[0]))
						.getType();
			}
			try {
				return gson.fromJson(response.result, returnType);
			}
			catch (Exception e) {
				// This can happen if there is no tab open
				log.trace("Failed to parse result, using default value", e);
				return automationRequest.defaultValue;
			}
		}
		catch (Exception e) {
			throw new AutomationException(e);
		}
		finally {
			if (messageId != null) {
				pendingMessages.remove(messageId);
			}
		}
	}

	@Override
	public void close() {
		try {
			server.stop();
			cleaner.shutdown();
		}
		catch (InterruptedException e) {
			throw new AutomationException(e);
		}
	}

	private class Server extends WebSocketServer {
		public Server(int port) {
			super(new InetSocketAddress(port));
		}
		@Override
		public void onOpen(WebSocket conn, ClientHandshake handshake) {
			final String id = getId(conn);
			if (clientConnections.containsKey(id)) {
				log.error("Duplicate connection requested for id {}", id);
				conn.close();
				return;
			}
			log.debug("Connection opened: {}", id);
			clientConnections.put(id, conn);
			final WaitForConnectionContext waitForConnectionContext = pendingConnections.get(id);
			if (waitForConnectionContext != null) {
				waitForConnectionContext.getCountdownLatch().countDown();
			}
			final Future<?> cleanTask = cleanTasks.remove(id);
			if (cleanTask != null) {
				// Was previously connected
				cleanTask.cancel(false);
				sendEvent(id, new ClientReconnectedEvent());
			}
			else {
				if (onConnect != null) {
					final AutomationClient client = new AutomationClientImpl(AutomationServerImpl.this, id);
					try {
						onConnect.accept(client);
					}
					catch (Exception e) {
						log.warn("Exception while processing onConnect handler", e);
					}
				}
			}
		}
		@Override
		public void onClose(WebSocket conn, int code, String reason, boolean remote) {
			final String id = getId(conn);
			clientConnections.remove(id);
			log.debug("Connection closed: {}", id);
			// Let's hold onto the client data in case it reconnects
			final Future<?> cleanTask = cleaner.schedule(() -> clean(id), 30, TimeUnit.MINUTES);
			cleanTasks.put(id, cleanTask);
			sendEvent(id, new ClientDisconnectedEvent());
		}
		@Override
		public void onMessage(WebSocket conn, String message) {
			final String id = getId(conn);
			log.debug("Received message from {}: {}", id, message);
			final JsonElement element = JsonParser.parseString(message);
			if (!element.isJsonObject()) {
				return;
			}
			if (element.getAsJsonObject().has("method")) {
				final Request request = gson.fromJson(message, Request.class);
				if ("onEvent".equals(request.getMethod())) {
					final String type = request.params.getAsJsonObject().get("eventType").getAsString();
					final var klass = AutomationEventType.getClassFor(type);
					final var event = gson.fromJson(request.getParams(), klass);
					sendEvent(id, event);
				}
				else if (AutomationEventType.CUSTOM_REQUEST.getName().equals(request.getMethod())) {
					final String body = request.getParams().getAsJsonArray().get(0).getAsString();
					final var event = new CustomRequestEvent(body, request.getId());
					sendEvent(id, event);
				}
			}
			else {
				final Response response = gson.fromJson(message, Response.class);
				final CompletableFuture<Response> responseCompletableFuture = pendingMessages.remove(response.getId());
				if (responseCompletableFuture != null) {
					responseCompletableFuture.complete(response);
				}
			}
		}
		@Override
		public void onError(WebSocket conn, Exception ex) {
			log.error("Websocket server error", ex);
		}
		@Override
		public void onStart() {
			log.trace("Started automation server");
		}
		private String getId(WebSocket conn) {
			final String id = conn.getAttachment();
			if (id != null) {
				return id;
			}
			if (conn.getResourceDescriptor().length() > 0) {
				conn.setAttachment(conn.getResourceDescriptor().substring(1));
			}
			else {
				conn.setAttachment(UUID.randomUUID().toString());
			}
			return conn.getAttachment();
		}
	}

	@Value
	@Builder
	private static class Request {
		@Builder.Default
		private final String jsonrpc = "2.0";
		@Builder.Default
		private final String id = UUID.randomUUID().toString();
		@NonNull
		private final String method;
		private final JsonElement params;
	}

	@Value
	private static class Response {
		private final String jsonrpc = "2.0";
		private final String id;
		private final JsonElement result;
		private final RpcError error;
	}

	@Value
	private static class RpcError {
		private final int code;
		private final String message;
		private final JsonObject data;
	}

	@Value
	@Builder
	static class AutomationRequest<R> {
		private final String method;
		@Singular
		private final List<?> params;
		private final Class<? extends R> returnType;
		@Singular
		private final List<Class<?>> returnTypeGenericTypes;
		private final R defaultValue;
	}

	@Value
	static class WaitForConnectionContext {
		private final String id;
		private final CountDownLatch countdownLatch = new CountDownLatch(1);
	}

	// Builder class generated by lombok, we need to specify we implement this interface
	static class AutomationServerImplBuilder implements AutomationServerBuilder {

	}

}
