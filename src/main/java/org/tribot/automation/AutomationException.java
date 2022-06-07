package org.tribot.automation;

/**
 * Thrown when there is an issue somewhere in the automation system, such as no response, or trying to perform an
 * operation on a closed client.
 */
public class AutomationException extends RuntimeException {

    AutomationException(Throwable source) {
        super(source);
    }

    AutomationException(String message) {
        super(message);
    }

}
