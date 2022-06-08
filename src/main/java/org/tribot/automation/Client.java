package org.tribot.automation;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Represents a client that can be launched. This will utilize TRiBot's CLI. All fields are optional.
 */
@Builder(toBuilder = true)
@Value
@Slf4j
public class Client {

	private static final TribotSplash splash = new TribotSplash();
	private static final File NULL_FILE = new File((System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null"));
	private static final Pattern LAUNCHED_PID_REGEX = Pattern.compile(".*tribotpid:(\\d+).*");

	/**
	 * The initial script name to run
	 */
	private final String scriptName;
	/**
	 * The script arguments to use for the script
	 */
	private final String scriptArguments;

	/**
	 * The break profile name to start the script with
	 */
	private final String breakProfileName;

	/**
	 * The account settings to start the script with
	 */
	private final Account account;

	/**
	 * The starting world
	 */
	private final int startingWorld;

	/**
	 * The proxy to use for the client
	 */
	private final Proxy proxy;

	/**
	 * The heap size to start the client with
	 */
	private final int heapSize;

	/**
	 * Whether the client should be minimized after launching.
	 */
	private final boolean minimizeOnLaunch;

	/**
	 * Whether to use looking glass
	 */
	private final boolean lookingGlass;
	/**
	 * The path to the third party client to use with looking glass
	 */
	private final String lookingGlassClientPath;

	/**
	 * Whether to use the tribot client beta version
	 */
	private final boolean betaClient;

	/**
	 * The development settings to be used by scripters/developers
	 */
	private final DevelopmentSettings developmentSettings;

	/**
	 * Whether to keep the launcher process open and print additional debug to {@code log.debug}
	 */
	private final boolean debug;

	/**
	 * The ssh settings to indicate the client should be launched on a remote machine
	 */
	private final SshSettings sshSettings;

	/**
	 * Launches the client and waits for it to be launched
	 *
	 * @throws LaunchException if there was an issue launching the client
	 */
	public void launch() throws LaunchException {
		try {
			log.debug("Launching client");
			final String id = UUID.randomUUID().toString();
			final String splashPath = updateSplash();
			launchProcess(splashPath, id, null);
		}
		catch (Exception e) {
			log.debug("Failed to launch client", e);
			throw new LaunchException(e);
		}
	}

	/**
	 * Launches the client and waits for it to be launched and connected to the automation server
	 *
	 * @param automationServer the automation server to connect the client to
	 * @return an automation client representing the launched client
	 * @throws LaunchException if there was an issue launching the client
	 */
	public AutomationClient launch(AutomationServer automationServer) throws LaunchException {
		if (!(automationServer instanceof AutomationServerImpl)) {
			throw new LaunchException("Unknown automation server: " + automationServer + ". Server must be created through AutomationServer#create");
		}
		final AutomationServerImpl automationServerImpl = (AutomationServerImpl) automationServer;
		try {
			log.debug("Launching client with automation server");
			final String id = UUID.randomUUID().toString();
			final String splashPath = updateSplash();
			final var waitForConnectionContext = new AutomationServerImpl.WaitForConnectionContext(id);
			try {
				automationServerImpl.register(waitForConnectionContext);
				launchProcess(splashPath, id, automationServerImpl);
				log.debug("Waiting for automation connection {}", id);
				final boolean connected = waitForConnectionContext.getCountdownLatch()
						.await(60, TimeUnit.SECONDS);
				log.debug("Automation client connected {}: {}", id, connected);
				if (!connected) {
					throw new IllegalStateException("No automation connection found after launch");
				}
				return new AutomationClientImpl(automationServerImpl, id);
			}
			finally {
				automationServerImpl.unregister(waitForConnectionContext);
			}
		}
		catch (Exception e) {
			log.debug("Failed to launch client", e);
			throw new LaunchException(e);
		}
	}

	private String updateSplash() throws Exception {
		return this.getSshSettings() != null
		       ? splash.update(this.getSshSettings())
		       : splash.update();
	}

	// This also waits till the client is open
	private long extractPid(Process process) {
		try (final BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			while (true) {
				final String line = br.readLine();
				if (line == null) {
					throw new LaunchException("Failed to find client after launch");
				}
				log.trace("Output from launched client: " + line);
				final Matcher matcher = LAUNCHED_PID_REGEX.matcher(line);
				if (matcher.matches()) {
					if (debug) {
						br.lines().forEach(s -> log.debug("Client output: " + s));
					}
					return Long.parseLong(matcher.group(1));
				}
			}
		}
		catch (IOException e) {
			throw new LaunchException(e);
		}
	}

	private long launchProcess(String tribotSplashPath, String id, AutomationServerImpl automationServer)
			throws Exception {
		final List<String> args = getArguments(tribotSplashPath, id, automationServer);
		log.debug("Generated client arguments: " + args);
		if (this.getSshSettings() != null) {
			log.trace("SSH config provided; launching through ssh");
			return launchRemoteClient(args);
		}
		final Process localProcess = new ProcessBuilder()
				.redirectErrorStream(true)
				.redirectInput(NULL_FILE)
				.command(args)
				.redirectOutput(ProcessBuilder.Redirect.PIPE)
				.start();
		return extractPid(localProcess);
	}

	private long launchRemoteClient(List<String> args) throws JSchException, InterruptedException {
		final Session session = this.getSshSettings().createSession();
		try {
			final ChannelExec channel = (ChannelExec) session.openChannel("exec");
			try {
				final String cmd = args.stream()
				                       .map(s -> {
										   if (s.contains(" ")) {
											   return '"' + s + '"';
										   }
										   return s;
				                       })
				                       .collect(Collectors.joining(" "));
				log.debug("Launching remote client: {}", cmd);
				if (getSshSettings().getDisplay() != null && !getSshSettings().getDisplay().isEmpty()) {
					channel.setCommand("export DISPLAY=" + getSshSettings().getDisplay() + " && " + cmd);
				}
				else {
					channel.setCommand(cmd);
				}
				ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
				channel.setOutputStream(responseStream);
				ByteArrayOutputStream errorResponseStream = new ByteArrayOutputStream();
				channel.setErrStream(errorResponseStream);
				channel.connect();
				while (channel.isConnected()) {
					Thread.sleep(100);
				}
				log.debug("Error stream: {}", errorResponseStream);
				return responseStream.toString().lines()
						.peek(s -> log.trace("Output from launched client: {}", s))
						.map(LAUNCHED_PID_REGEX::matcher)
						.filter(Matcher::matches)
						.findFirst()
						.map(m -> m.group(1))
						.map(Long::parseLong)
						.orElseThrow(() -> new IllegalStateException("Failed to find launch process id: error " +
						                                             "stream: " + errorResponseStream.toString()));
			}
			finally {
				channel.disconnect();
			}
		}
		finally {
			session.disconnect();
		}
	}

	private List<String> getArguments(String tribotSplashPath, String id, AutomationServerImpl automationServer) {

		final List<String> args = new ArrayList<>();

		if (this.getSshSettings() == null) {
			args.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
		}
		else {
			// For now, we require the remote machine has java installed
			args.add("java");
		}
		args.add("-jar");
		args.add(tribotSplashPath);

		// So we can get the PID
		args.add("--debug");

		if (account != null) {
			tryAddString(args, "--charusername", account.getUsername());
			tryAddString(args, "--charpassword", account.getPassword());
			tryAddString(args, "--charpin", account.getPin());
			tryAddString(args, "--chartotp", account.getTotpSecret());
		}

		tryAddString(args, "--script", getScriptName());
		tryAddString(args, "--scriptargs", getScriptArguments());
		tryAddString(args, "--breakprofile", getBreakProfileName());

		if (getStartingWorld() != 0) {
			args.add("--charworld");
			args.add(getStartingWorld() + "");
		}

		if (proxy != null) {
			tryAddString(args, "--proxyhost", proxy.getIp());
			if (proxy.getPort() > 0) {
				args.add("--proxyport");
				args.add(proxy.getPort() + "");
			}
			tryAddString(args, "--proxyusername", proxy.getUsername());
			tryAddString(args, "--proxypassword", proxy.getPassword());
		}

		if (getHeapSize() != 0) {
			args.add("--mem");
			args.add(getHeapSize() + "");
		}

		if (isMinimizeOnLaunch()) {
			args.add("--minimize");
		}

		if (isLookingGlass()) {
			args.add("--lgpath");
			args.add(getLookingGlassClientPath());
			args.add("--lgdelay");
			args.add("15");
			if (getLookingGlassClientPath().toLowerCase().contains("openosrs")) {
				args.add("--lgargs");
				args.add("\"--stable\"");
			}
			else if (getLookingGlassClientPath().toLowerCase().contains("jagexlauncher")) {
				args.add("--lgargs");
				args.add("oldschool");
			}
		}

		if (automationServer != null) {
			args.add("--automation-url");
			args.add(automationServer.getConnectionUrl(id, this.getSshSettings() == null));
		}

		if (getDevelopmentSettings() != null && getDevelopmentSettings().getDaxWalkerDevelopmentMode() != null) {
			args.add("--dax-walker-development-mode");
			args.add(getDevelopmentSettings().getDaxWalkerDevelopmentMode() + "");
			tryAddString(args, "--dax-walker-path", getDevelopmentSettings().getDaxWalkerJarPath());
		}

		if (getDevelopmentSettings() != null && getDevelopmentSettings().getSdkDevelopmentMode() != null) {
			args.add("--sdk-development-mode");
			args.add(getDevelopmentSettings().getSdkDevelopmentMode() + "");
			tryAddString(args, "--sdk-path", getDevelopmentSettings().getSdkJarPath());
		}

		if (isBetaClient()) {
			args.add("--beta");
		}

		if (getDevelopmentSettings() != null && getDevelopmentSettings().isRemoteDebugging()) {
			args.add("--remote-debugger");
			tryAddString(args, "--remote-debugger-port", getDevelopmentSettings().getRemoteDebuggingPort());
		}

		return args;
	}

	private void tryAddString(List<String> args, String key, Object value) {
		if (value == null) {
			return;
		}
		final String string = value.toString();
		if (string.trim().isEmpty()) {
			return;
		}
		args.add(key);
		args.add(tryQuote(string.trim()));
	}

	private String tryQuote(String s) {
		if (s.contains(",")) {
			return quote(s);
		}
		return s;
	}

	private String quote(String s) {
		return "\"" + s + "\"";
	}

}
