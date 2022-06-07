package org.tribot.automation;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a skill to be used when getting skill levels through an {@link AutomationClient}
 */
@RequiredArgsConstructor
@Getter(AccessLevel.PACKAGE)
public enum Skill {

	ATTACK("attack"),
	DEFENCE("defence"),
	STRENGTH("strength"),
	HITPOINTS("hitpoints"),
	RANGED("ranged"),
	PRAYER("prayer"),
	MAGIC("magic"),
	COOKING("cooking"),
	WOODCUTTING("woodcutting"),
	FLETCHING("fletching"),
	FISHING("fishing"),
	FIREMAKING("firemaking"),
	CRAFTING("crafting"),
	SMITHING("smithing"),
	MINING("mining"),
	HERBLORE("herblore"),
	AGILITY("agility"),
	THIEVING("thieving"),
	SLAYER("slayer"),
	FARMING("farming"),
	RUNECRAFTING("runecrafting"),
	HUNTER("hunter"),
	CONSTRUCTION("construction");

	private final String name;

}
