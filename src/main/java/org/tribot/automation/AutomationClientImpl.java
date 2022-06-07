package org.tribot.automation;

import lombok.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode(of = "id")
@Getter(AccessLevel.PACKAGE)
class AutomationClientImpl implements AutomationClient {

	private static final int TAB_ID = -1;

	private final AutomationServerImpl automationServer;
	private final String id;

	@Override
	public boolean isRunning() {
		return automationServer.isConnected(id);
	}

	@Override
	public void onBan(Runnable runnable) {
		on(BanEvent.class, e -> runnable.run());
	}

	@Override
	public void onScriptEnd(Runnable runnable) {
		on(ScriptEndEvent.class, e -> runnable.run());
	}

	@Override
	public void onDisconnect(Runnable runnable) {
		on(ClientDisconnectedEvent.class, e -> runnable.run());
	}

	@Override
	public void onReconnect(Runnable runnable) {
		on(ClientReconnectedEvent.class, e -> runnable.run());
	}

	@Override
	public void onScriptMessage(Consumer<String> consumer) {
		on(CustomMessageEvent.class, e -> consumer.accept(e.getMessage()));
	}

	@Override
	public void onScriptRequest(Function<String, Object> requestHandler) {
		on(CustomRequestEvent.class, e -> {
			try {
				automationServer.sendResponse(id, e.getId(), requestHandler.apply(e.getRequest()));
			}
			catch (Exception ex) {
				automationServer.sendResponse(id, e.getId(), ex);
			}
		});
	}

	@Override
	public void sendScriptMessage(String message) throws AutomationException {
		this.request()
			.method("sendScriptMessage")
			.param(TAB_ID)
			.param(message)
			.build()
			.execute();
	}

	@Override
	public <T> T sendScriptRequest(String request, Class<T> returnType) throws AutomationException {
		return this.<T>request()
			.method("sendScriptRequest")
			.returnType(returnType)
			.param(request)
			.build()
			.execute();
	}

	@Override
	public void startScript(StartScriptRequest startScriptRequest) throws AutomationException {
		this.request()
		    .method("startScript")
		    .param(TAB_ID)
		    .param(startScriptRequest.getPassword() != null ?
		           Map.of("username", startScriptRequest.getUsername(), "password", startScriptRequest.getPassword()) :
		           startScriptRequest.getUsername())
		    .param(startScriptRequest.getScriptName())
		    .param(startScriptRequest.getScriptArguments())
		    .param(startScriptRequest.getBreakProfileName())
		    .build()
		    .execute();
	}

	@Override
	public void stopScript() throws AutomationException {
		this.request()
			.method("stopScript")
			.param(TAB_ID)
			.build()
			.execute();
	}

	@Override
	public void pauseScript() throws AutomationException {
		this.request()
			.method("pauseScript")
			.param(TAB_ID)
			.build()
			.execute();
	}

	@Override
	public void resumeScript() throws AutomationException {
		this.request()
			.method("unPauseScript")
			.param(TAB_ID)
			.build()
			.execute();
	}

	@Override
	public String getScriptName() throws AutomationException {
		final TabInfo tabInfo = this.<TabInfo>request()
			.method("getTabInfo")
			.param(TAB_ID)
			.returnType(TabInfo.class)
			.build()
			.execute();
		if (tabInfo == null) {
			return null;
		}
		return tabInfo.script;
	}

	@Override
	public String getLoginName() throws AutomationException {
		final TabInfo tabInfo = this.<TabInfo>request()
			.method("getTabInfo")
			.param(TAB_ID)
			.returnType(TabInfo.class)
			.build()
			.execute();
		if (tabInfo == null) {
			return null;
		}
		return tabInfo.username;
	}

	@Override
	public Image getScreenshot() throws AutomationException {
		final String screenshotBase64 = this.<String>request()
			.method("getScreenshot")
			.param(TAB_ID)
			.returnType(String.class)
			.build()
			.execute();
		if (screenshotBase64 == null) {
			return null;
		}
		final byte[] bytes = Base64.getDecoder().decode(screenshotBase64);
		final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
		try {
			return ImageIO.read(byteArrayInputStream);
		}
		catch (IOException e) {
			throw new AutomationException(e);
		}
	}

	@Override
	public String getUsername() throws AutomationException {
		return this.<String>request()
				.method("getUsername")
				.param(TAB_ID)
				.returnType(String.class)
				.build()
				.execute();
	}

	@Override
	public int getSkillLevel(Skill skill) throws AutomationException {
		return this.<Integer>request()
				.method("getStat")
				.param(TAB_ID)
				.param(skill.getName())
				.returnType(Integer.class)
				.defaultValue(1)
				.build()
				.execute();
	}

	@Override
	public Map<Skill, Integer> getAllSkillLevels() throws AutomationException {
		return this.request()
				.method("getAllStats")
				.param(TAB_ID)
				.returnType(Map.class)
				.returnTypeGenericType(String.class)
				.returnTypeGenericType(Integer.class)
				.defaultValue(Map.of())
				.build()
				.<Map<String, Integer>>executeUnchecked()
				.entrySet()
				.stream()
				.collect(Collectors.toMap(e -> Skill.valueOf(e.getKey()), Map.Entry::getValue));
	}

	@Override
	public List<Item> getInventoryItems() throws AutomationException {
		return this.request()
				.method("getInventoryItems")
				.param(TAB_ID)
				.returnType(List.class)
				.returnTypeGenericType(int[].class)
				.defaultValue(List.of())
				.build()
				.<List<int[]>>executeUnchecked()
				.stream()
				.map(i -> new Item(i[0], i[1]))
				.collect(Collectors.toList());
	}

	@Override
	public Tile getTile() throws AutomationException {
		final int[] position = this.<int[]>request()
				.method("getPosition")
				.param(TAB_ID)
				.returnType(int[].class)
				.build()
				.execute();
		if (position == null || position[0] < 0 || position[1] < 0) {
			return null;
		}
		return new Tile(position[0], position[1], position[2]);
	}

	@Override
	public boolean isLoggedIn() throws AutomationException {
		return this.<Boolean>request()
				.method("isLoggedIn")
				.param(TAB_ID)
				.returnType(Boolean.class)
				.defaultValue(false)
				.build()
				.execute();
	}

	@Override
	public int getWorld() throws AutomationException {
		return this.<Integer>request()
		           .method("getWorld")
		           .param(TAB_ID)
		           .returnType(Integer.class)
		           .defaultValue(-1)
		           .build()
		           .execute();
	}

	@Override
	public int getGameSetting(int index) throws AutomationException {
		return this.<Integer>request()
				.method("getGameSetting")
				.param(TAB_ID)
				.param(index)
				.returnType(Integer.class)
				.defaultValue(-1)
				.build()
				.execute();
	}

	@Override
	public int getVarbit(int index) throws AutomationException {
		return this.<Integer>request()
				.method("getVarbit")
				.param(TAB_ID)
				.param(index)
				.returnType(Integer.class)
				.defaultValue(-1)
				.build()
				.execute();
	}

	private <R> Request.RequestBuilder<R> request() {
		return Request.<R>builder()
				.automationServer(automationServer)
				.clientId(id);
	}

	private <T extends AutomationEvent> void on(Class<T> eventClass, Consumer<T> onEvent) {
		automationServer.onEvent(id, eventClass, onEvent);
	}

	@Override
	public void close() {
		try {
			request()
					.method("killProcess")
					.build()
					.execute();
		}
		catch (AutomationException ignored) { }
	}

	@Value
	private static class TabInfo {
		private final long id;
		private final String username;
		private final String script;
	}

	@Builder
	private static class Request<R> {
		private final String method;
		@Singular
		private final List<?> params;
		private final Class<? extends R> returnType;
		@Singular
		private final List<Class<?>> returnTypeGenericTypes;
		private final AutomationServerImpl automationServer;
		private final String clientId;
		private final R defaultValue;
		public R execute() {
			final var automationRequest =
					AutomationServerImpl.AutomationRequest.<R>builder()
							.method(method)
							.params(params)
							.returnType(returnType)
							.returnTypeGenericTypes(returnTypeGenericTypes)
							.defaultValue(defaultValue)
							.build();
			return automationServer.send(clientId, automationRequest);
		}
		public <T> T executeUnchecked() {
			// Generic type returns need to be casted, such as Map<String, Integer>
			return (T) execute();
		}
	}

}
