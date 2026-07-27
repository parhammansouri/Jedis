package jedis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    private final Map<String, String> store = new ConcurrentHashMap<String, String>();
    private final Object commandLock = new Object();

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            return;
        }
        new Main().serve(Integer.parseInt(args[0]));
    }

    private void serve(int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port, 1024);
        while (true) {
            final Socket socket = serverSocket.accept();
            new Thread(new Runnable() {
                public void run() {
                    handle(socket);
                }
            }).start();
        }
    }

    private void handle(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(process(line));
                writer.newLine();
                writer.flush();
            }
        } catch (IOException ignored) {
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private String process(String line) {
        synchronized (commandLock) {
            if (line == null || line.trim().length() == 0) {
                return "ERR empty command";
            }
            String[] parts = line.trim().split("\\s+");
            String command = parts[0];
            if ("SET".equals(command)) {
                if (parts.length != 3) {
                    return "ERR wrong number of arguments for 'SET'";
                }
                store.put(parts[1], parts[2]);
                return "OK";
            }
            if ("GET".equals(command)) {
                if (parts.length != 2) {
                    return "ERR wrong number of arguments for 'GET'";
                }
                String value = store.get(parts[1]);
                return value == null ? "NULL" : value;
            }
            if ("DEL".equals(command)) {
                if (parts.length != 2) {
                    return "ERR wrong number of arguments for 'DEL'";
                }
                return store.remove(parts[1]) == null ? "0" : "1";
            }
            if ("PING".equals(command)) {
                if (parts.length == 1) {
                    return "PONG";
                }
                return "ERR unknown command 'PING'";
            }
            return "ERR unknown command '" + command + "'";
        }
    }
}
