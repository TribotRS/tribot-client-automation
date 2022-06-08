package org.tribot.automation;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * Represents an ssh configuration to use when launching a client. It will indicate that the client should
 * be launched on a remote machine.
 */
@Value
@Builder
public class SshSettings {

    /**
     * The username to log into ssh with
     */
    @NonNull
    private final String username;
    /**
     * The host to log in to
     */
    @NonNull
    private final String host;
    /**
     * The SSH port. Defaults to 22
     */
    @Builder.Default
    private final int port = 22;
    /**
     * The ssh password to go along with the username, if required
     */
    private final String password;
    /**
     * The DISPLAY environment variable. This should point to wherever your graphical display is on your linux machine.
     * Run `printenv DISPLAY` to find the DISPLAY you are using.
     * Ignore this if connecting to a Windows machine.
     */
    @Builder.Default
    private final String display = ":10.0";

    Session createSession() throws JSchException {
        final Session session = new JSch().getSession(getUsername(), getHost(), getPort());
        if (getPassword() != null && !getPassword().isEmpty()) {
            session.setPassword(getPassword());
            session.setConfig("PreferredAuthentications", "password");
            session.setUserInfo(new UserInfo() {
                @Override
                public String getPassphrase() {
                    return getPassword();
                }

                @Override
                public String getPassword() {
                    return password;
                }

                @Override
                public boolean promptPassword(final String message) {
                    return true;
                }

                @Override
                public boolean promptPassphrase(final String message) {
                    return true;
                }

                @Override
                public boolean promptYesNo(final String message) {
                    return true;
                }

                @Override
                public void showMessage(final String message) {

                }
            });
        }
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();
        return session;
    }

}
