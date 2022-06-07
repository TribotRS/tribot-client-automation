package org.tribot.automation;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * Represents a proxy to be used for a client
 */
@Value
@Builder
public class Proxy {

	/**
	 * The proxy ip/hostname to connect to
	 */
	@NonNull
	private final String ip;
	/**
	 * The proxy port to use. Defaults to 1080.
	 */
	@Builder.Default
	private final int port = 1080;
	/**
	 * The proxy username, if required
	 */
	private final String username;
	/**
	 * The proxy password, if required
	 */
	private final String password;

}
