package org.tribot.automation;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * Represents a start script request to be used with {@link AutomationClient#startScript(StartScriptRequest)}
 */
@Builder
@Value
public class StartScriptRequest {

	/**
	 * The username for the account to run the script with
	 */
	private final String username;
	/**
	 * The password for the account to run the script with
	 */
	private final String password;
	/**
	 * The break profile name to run the script with
	 */
	private final String breakProfileName;
	/**
	 * The script name to run
	 */
	@NonNull
	private final String scriptName;
	/**
	 * The script arguments to run the script with
	 */
	private final String scriptArguments;

}
