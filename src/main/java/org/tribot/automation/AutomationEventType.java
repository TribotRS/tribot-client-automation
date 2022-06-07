package org.tribot.automation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@RequiredArgsConstructor
@Getter
enum AutomationEventType {

    SCRIPT_END("scriptEnded", ScriptEndEvent.class),
    BAN("banned", BanEvent.class),
    CUSTOM("custom", CustomMessageEvent.class),

    CUSTOM_REQUEST("sendCustomRequest", CustomRequestEvent.class),
    DISCONNECTED("disconnected", ClientDisconnectedEvent.class),
    RECONNECTED("reconnected", ClientReconnectedEvent.class)
    ;
    private final String name;
    private final Class<? extends AutomationEvent> automationEventClass;

    // If this is a custom event server side, not from the automation client
    public boolean isCustom() {
        switch (this) {
            case DISCONNECTED:
            case RECONNECTED:
            case CUSTOM_REQUEST:
                return true;
            default:
                return false;
        }
    }

    public static String getNameFor(Class<? extends AutomationEvent> automationEventClass) {
        return getByClass(automationEventClass).getName();
    }

    public static AutomationEventType getByClass(Class<? extends AutomationEvent> automationEventClass) {
        return Arrays.stream(values())
                .filter(a -> a.automationEventClass == automationEventClass)
                .findFirst()
                .orElseThrow();
    }

    public static Class<? extends AutomationEvent> getClassFor(String name) {
        return Arrays.stream(values())
                .filter(a -> a.name.equals(name))
                .findFirst()
                .map(AutomationEventType::getAutomationEventClass)
                .orElseThrow();
    }

}
