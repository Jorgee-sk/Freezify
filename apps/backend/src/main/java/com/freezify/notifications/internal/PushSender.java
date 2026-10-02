package com.freezify.notifications.internal;

import java.util.Map;

/** Delivers a message to one app installation. Implementations never throw: a failed delivery is a result. */
interface PushSender {

    /** Whether anything can be sent at all; when not, callers need not prepare messages. */
    boolean enabled();

    Result send(String deviceToken, PushMessage message);

    /**
     * @param data what the app needs to open the right screen when the message is tapped
     */
    record PushMessage(String title, String body, Map<String, String> data) {}

    enum Result {
        SENT,
        /** The installation is gone (app removed, token replaced): the token must be forgotten. */
        UNREGISTERED,
        /** Anything else; the token is kept and the failure logged by the sender. */
        FAILED
    }
}

/** Stands in when no push service is configured. */
class DisabledPushSender implements PushSender {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public Result send(String deviceToken, PushMessage message) {
        return Result.FAILED;
    }
}
