package org.tribot.automation;

import lombok.Value;

@Value
class CustomRequestEvent implements AutomationEvent {

	private final String request;
	private final String id;

}
