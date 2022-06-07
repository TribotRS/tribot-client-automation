package org.tribot.automation;

import lombok.Value;

/**
 * Development settings to be configured when launching a client
 */
@Value
public class DevelopmentSettings {

	/**
	 * Whether to use sdk development mode. Non-null will override the saved launcher advanced settings.
	 */
	private final Boolean sdkDevelopmentMode;
	/**
	 * The sdk jar path to override the default jar - for local development
	 */
	private final String sdkJarPath;

	/**
	 * Whether to use dax walker development mode. Non-null will override the saved launcher advanced settings.
	 */
	private final Boolean daxWalkerDevelopmentMode;
	/**
	 * The dax walker jar path to override the default jar - for local development
	 */
	private final String daxWalkerJarPath;

	/**
	 * Whether to enable remote debugging or not - see the TRiBot forums for more on remote debugging
	 */
	private final boolean remoteDebugging;
	/**
	 * The remote debugging port. Non-null will override the default.
	 */
	private final Integer remoteDebuggingPort;

}
