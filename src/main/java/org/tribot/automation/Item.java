package org.tribot.automation;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Represents an item in-game
 */
@Value
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class Item {

	/**
	 * The item ID
	 */
	private final int id;

	/**
	 * The item stack
	 */
	private final int stack;

}
