package org.tribot.automation;

/**
 * Indicates there was an issue launching a client, such as the process failing to be found after launch (not enough
 * memory for more clients)
 */
public class LaunchException extends RuntimeException {

	LaunchException(Throwable source) {
		super(source);
	}

	LaunchException(String message) {
		super(message);
	}

}
