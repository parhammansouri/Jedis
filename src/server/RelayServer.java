package server;

import common.Frame;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class RelayServer {
    private final ConcurrentHashMap<String, ClientSession> online = new ConcurrentHashMap<String, ClientSession>();
    private final ConcurrentHashMap<Integer, String> pending = new ConcurrentHashMap<Integer, String>();
    private final ConcurrentHashMap<String, Long> mutedUntil = new ConcurrentHashMap<String, Long>();
    private final ProfileStore profiles = new ProfileStore();
    private final OfflineQueueManager offline = new OfflineQueueManager();
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: java server.RelayServer <port>");
        }
        new RelayServer().start(Integer.parseInt(args[0]));
    }

    private void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        new Thread(new AdminConsole(), "RelayServer-admin").start();
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(500);
                new Thread(new ClientSession(socket), "RelayServer-client").start();
            } catch (SocketTimeoutException timeout) {
                System.err.println("Request Timed out.");
            } catch (IOException error) {
                if (running) {
                    throw error;
                }
            }
        }
    }

    private void register(ClientSession session, String payload) throws IOException {
        String[] pair = splitCredentials(payload);
        if (pair == null) {
            session.notice(">> [SYSTEM NOTICE]: Invalid registration request.");
            return;
        }
        if (profiles.exists(pair[0])) {
            session.notice(">> [SYSTEM NOTICE]: Username is already taken. Please choose another username.");
            return;
        }
        UserProfile profile = new UserProfile(pair[0], pair[1]);
        profiles.save(profile);
        session.loginAs(profile);
        session.notice(">> [SYSTEM NOTICE]: Registration successful! Welcome to \"Na-Kheyr\" Messenger.");
    }

    private void login(ClientSession session, String payload) throws IOException {
        String[] pair = splitCredentials(payload);
        if (pair == null) {
            session.notice(">> [SYSTEM NOTICE]: Invalid login request.");
            return;
        }
        try {
            UserProfile profile = profiles.load(pair[0]);
            if (profile == null) {
                session.notice(">> [SYSTEM NOTICE]: Account not found. Please register first or check your spelling.");
                return;
            }
            if (!profile.password.equals(pair[1])) {
                session.failedAttempts++;
                int remaining = 3 - session.failedAttempts;
                if (remaining <= 0) {
                    session.notice(">> [SYSTEM NOTICE]: Disconnected from server: Maximum password attempts exceeded.");
                    session.close();
                } else {
                    session.notice(">> [SYSTEM NOTICE]: Incorrect password. Attempts remaining: " + remaining);
                }
                return;
            }
            session.loginAs(profile);
            session.notice(">> [SYSTEM NOTICE]: Login successful! Welcome back, " + pair[0] + ".");
            deliverOffline(session);
        } catch (ClassNotFoundException error) {
            throw new IOException(error);
        }
    }

    private void lounge(ClientSession sender, String text) throws IOException {
        if (!sender.isAuthenticated()) {
            return;
        }
        if (isMuted(sender.username)) {
            sender.notice(">> [SYSTEM NOTICE]: You are currently muted and cannot send messages.");
            return;
        }
        sender.incrementSent();
        String formatted = "[LOUNGE] " + sender.username + ": " + text;
        for (ClientSession session : online.values()) {
            if (session != sender) {
                session.send(Frame.textFrame(Frame.LOUNGE, formatted));
                session.incrementReceived();
            }
        }
    }

    private void direct(ClientSession sender, Frame.DirectPayload message) throws IOException {
        if (!sender.isAuthenticated()) {
            return;
        }
        if (isMuted(sender.username)) {
            sender.notice(">> [SYSTEM NOTICE]: You are currently muted and cannot send messages.");
            return;
        }
        sender.incrementSent();
        ClientSession target = online.get(message.username);
        if (target != null) {
            sendDirectToOnline(sender.username, target, message.id, message.message);
            return;
        }
        if (profiles.exists(message.username)) {
            offline.enqueue(message.username, new OfflineMessage(message.id, sender.username, message.message));
            sender.notice(">> [SYSTEM NOTICE]: User " + message.username + " is offline. Message enqueued for offline delivery");
        } else {
            sender.notice(">> [SYSTEM NOTICE]: Target user " + message.username + " is offline or does not exist.");
        }
    }

    private void sendDirectToOnline(String sender, ClientSession target, int id, String text) throws IOException {
        pending.put(Integer.valueOf(id), target.username);
        target.send(Frame.direct(id, sender, "[DM] " + sender + ": " + text));
        target.incrementReceived();
    }

    private void deliverOffline(ClientSession session) throws IOException {
        try {
            List<OfflineMessage> messages = offline.drain(session.username);
            for (OfflineMessage message : messages) {
                sendDirectToOnline(message.sender, session, message.id, message.text);
            }
        } catch (ClassNotFoundException error) {
            throw new IOException(error);
        }
    }

    private void handleCommand(ClientSession session, String text) throws IOException {
        if ("/profile".equals(text)) {
            session.notice(profileText(session.profile));
        } else if (text.startsWith("/change_password ")) {
            changePassword(session, text);
        } else {
            lounge(session, text);
        }
    }

    private void changePassword(ClientSession session, String command) throws IOException {
        String[] parts = command.split(" ", 3);
        if (parts.length != 3 || parts[2].length() == 0 || parts[1].equals(parts[2])) {
            session.notice(">> [SYSTEM NOTICE]: Failed to update password. New password cannot be empty or duplicate.");
            return;
        }
        if (!session.profile.password.equals(parts[1])) {
            session.notice(">> [SYSTEM NOTICE]: Failed to update password. Current password does not match");
            return;
        }
        session.profile.password = parts[2];
        profiles.save(session.profile);
        session.notice(">> [SYSTEM NOTICE]: Password updated successfully.");
    }

    private String profileText(UserProfile profile) {
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < profile.password.length(); i++) {
            stars.append('*');
        }
        return "=== User Profile ===\n"
                + "Username: " + profile.username + "\n"
                + "Password: [HIDDEN] " + stars + "\n"
                + "Messages Sent: " + profile.sent + "\n"
                + "Messages Received: " + profile.received + "\n"
                + "====================";
    }

    private boolean isMuted(String username) {
        Long until = mutedUntil.get(username);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until.longValue()) {
            mutedUntil.remove(username);
            return false;
        }
        return true;
    }

    private String[] splitCredentials(String payload) {
        int space = payload.indexOf(' ');
        if (space <= 0 || space == payload.length() - 1) {
            return null;
        }
        return new String[] {payload.substring(0, space), payload.substring(space + 1)};
    }

    private void kick(String username) {
        ClientSession session = online.get(username);
        if (session != null) {
            try {
                session.send(new Frame(Frame.ROOT, new byte[] {(byte) Frame.ROOT_KICK}));
                session.notice(">> [SYSTEM NOTICE]: You have been kicked by the administrator.");
            } catch (IOException ignored) {
            }
            session.close();
            broadcastAnnouncement(username + " has been kicked by the administrator.");
        }
    }

    private void mute(String username, int seconds) {
        mutedUntil.put(username, Long.valueOf(System.currentTimeMillis() + seconds * 1000L));
        ClientSession session = online.get(username);
        if (session != null) {
            try {
                session.send(new Frame(Frame.ROOT, new byte[] {(byte) Frame.ROOT_MUTE}));
            } catch (IOException ignored) {
            }
        }
    }

    private void broadcastAnnouncement(String text) {
        String line = "[LOUNGE] \"ANNOUNCEMENT: " + text + "\"";
        for (ClientSession session : online.values()) {
            try {
                session.send(Frame.textFrame(Frame.LOUNGE, line));
            } catch (IOException ignored) {
            }
        }
    }

    private void shutdown() {
        running = false;
        for (ClientSession session : online.values()) {
            try {
                session.notice(">> [SYSTEM NOTICE]: Server is shutting down. Disconnecting...");
            } catch (IOException ignored) {
            }
            session.close();
        }
        offline.flushAll();
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private final class ClientSession implements Runnable {
        private final Socket socket;
        private final DataInputStream input;
        private final DataOutputStream output;
        private String username;
        private UserProfile profile;
        private int failedAttempts;
        private volatile boolean open = true;

        ClientSession(Socket socket) throws IOException {
            this.socket = socket;
            this.input = new DataInputStream(socket.getInputStream());
            this.output = new DataOutputStream(socket.getOutputStream());
        }

        public void run() {
            try {
                while (open && running) {
                    Frame frame = Frame.read(input);
                    if (frame == null) {
                        break;
                    }
                    if (!isAuthenticated() && frame.type != Frame.REGISTER && frame.type != Frame.LOGIN) {
                        continue;
                    }
                    if (frame.type == Frame.REGISTER) {
                        register(this, frame.text());
                    } else if (frame.type == Frame.LOGIN) {
                        login(this, frame.text());
                    } else if (frame.type == Frame.LOUNGE) {
                        handleCommand(this, frame.text());
                    } else if (frame.type == Frame.DIRECT) {
                        direct(this, Frame.directPayload(frame.payload));
                    } else if (frame.type == Frame.ACK) {
                        pending.remove(Integer.valueOf(Frame.ackId(frame.payload)));
                    }
                }
            } catch (SocketTimeoutException timeout) {
                System.err.println("Request Timed out.");
            } catch (EOFException ignored) {
            } catch (IOException ignored) {
            } finally {
                close();
            }
        }

        boolean isAuthenticated() {
            return username != null;
        }

        void loginAs(UserProfile profile) {
            if (username != null) {
                online.remove(username, this);
            }
            this.profile = profile;
            this.username = profile.username;
            online.put(username, this);
            failedAttempts = 0;
        }

        synchronized void send(Frame frame) throws IOException {
            frame.write(output);
        }

        void notice(String text) throws IOException {
            send(Frame.textFrame(Frame.NOTICE, text));
        }

        void incrementSent() throws IOException {
            profile.sent++;
            profiles.save(profile);
        }

        void incrementReceived() throws IOException {
            profile.received++;
            profiles.save(profile);
        }

        void close() {
            open = false;
            if (username != null) {
                online.remove(username, this);
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private final class AdminConsole implements Runnable {
        public void run() {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
            while (running) {
                try {
                    String line = reader.readLine();
                    if (line == null) {
                        return;
                    }
                    if ("/list".equals(line)) {
                        System.out.println("Online users: " + new ArrayList<String>(online.keySet()));
                    } else if (line.startsWith("/kick ")) {
                        kick(line.substring(6).trim());
                    } else if (line.startsWith("/mute ")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length == 3) {
                            mute(parts[1], Integer.parseInt(parts[2]));
                        }
                    } else if ("/shutdown".equals(line)) {
                        shutdown();
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }
}
