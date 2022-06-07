package org.tribot.automation;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Logger;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class Startup {

	static {
		// Yes this is a hack
		JSch.setLogger(new Logger() {
			@Override
			public boolean isEnabled(final int level) {
				return true;
			}
			@Override
			public void log(final int level, final String message) {
				log.debug(level +": " + message);
			}
		});
	}

	@Test
	public void test() throws InterruptedException {
		final AutomationServer automationServer = AutomationServer.builder()
		                                                          .build();
		final Client client = Client.builder()
				.account(Account.builder()
						         .username("test")
						         .password("asdf")
				                .build())
				.scriptName("nRestocker")
				.scriptArguments("settings:last")
				.betaClient(true)
				.build();
		final AutomationClient clientContext = client.launch(automationServer);
		clientContext.getInventoryItems()
				.stream()
				.filter(i -> i.getId() == 995)
				.findFirst()
				.ifPresent(i -> {
					System.out.println("We have " + i.getStack() + " coins");
				});
		Thread.sleep(1000000);
		clientContext.sendScriptMessage("test");
		clientContext.close();
	}

	@Test
	public void remote() {
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
	}

	@Test
	public void automationServer() throws InterruptedException {
		final AutomationServer server = AutomationServer
				.builder()
				.port(8080)
				.onConnect(client -> {
					final StartScriptRequest startScriptRequest = StartScriptRequest.builder()
							.username("test")
							.password("test")
							.scriptName("nRestocker")
							.scriptArguments("settings:last")
							.build();
					client.startScript(startScriptRequest);
				})
				.build();
		//Client.builder().build().launch(server);
		Thread.sleep(100000);
		server.close();
	}

}
