package org.tribot.automation;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Represents a tile in-game
 */
@Value
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class Tile {

	/**
	 * The x-coordinate
	 */
	private final int x;
	/**
	 * The y-coordinate
	 */
	private final int y;
	/**
	 * The z-coordinate (plane)
	 */
	private final int plane;

}
