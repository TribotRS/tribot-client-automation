package org.tribot.automation;

import lombok.Value;

@Value
class CustomMessageEvent implements AutomationEvent {

	private final String message;

}
