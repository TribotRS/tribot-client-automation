package org.tribot.automation;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * Represents a game account
 */
@Value
@Builder
public class Account {

	/**
	 * The login name to log into the account with
	 */
	@NonNull
	private final String username;

	/**
	 * The password to log into the account with
	 */
	private final String password;

	/**
	 * The bank pin for when the client opens the bank
	 */
	private final String pin;

	/**
	 * The totp secret to handle the authenticator, if required
	 */
	private final String totpSecret;

}
