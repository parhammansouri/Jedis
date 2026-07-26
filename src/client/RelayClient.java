package client;

import common.Frame;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.Random;

public class RelayClient {
    private final Random random = new Random();
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: java client.RelayClient <host> <port>");
        }
        new RelayClient().start(args[0], Integer.parseInt(args[1]));
    }

    private void start(String host, int port) throws Exception {
        System.out.println("Connecting to Relay Server...");
        socket = new Socket(host, port);
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
        System.out.println("Connected!");
        System.out.println("========================================");
        System.out.println("Welcome to \"Na-Kheyr\" Messenger!");

        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        authenticate(console);
        new Thread(new Listener(), "RelayClient-listener").start();
        readConsole(console);
    }

    private void authenticate(BufferedReader console) throws IOException {
        while (running) {
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.print("Choose an option (1 or 2): ");
            String choice = console.readLine();
            if ("1".equals(choice)) {
                System.out.println("[Register]");
                System.out.print("Enter desired username: ");
                String username = console.readLine();
                System.out.print("Enter password: ");
                String password = console.readLine();
                send(Frame.textFrame(Frame.REGISTER, username + " " + password));
                return;
            }
            if ("2".equals(choice)) {
                System.out.println("[Login]");
                System.out.print("Enter username: ");
                String username = console.readLine();
                System.out.print("Enter password: ");
                String password = console.readLine();
                send(Frame.textFrame(Frame.LOGIN, username + " " + password));
                return;
            }
        }
    }

    private void readConsole(BufferedReader console) throws IOException {
        while (running) {
            String line = console.readLine();
            if (line == null || "exit".equals(line)) {
                close();
                return;
            }
            if (line.startsWith("@")) {
                int space = line.indexOf(' ');
                if (space > 1) {
                    String target = line.substring(1, space);
                    String text = line.substring(space + 1);
                    int id = Math.abs(random.nextInt(900000)) + 100000;
                    send(Frame.direct(id, target, text));
                    System.out.println("[Sent DM to " + target + " with ID " + id + "]");
                }
            } else {
                send(Frame.textFrame(Frame.LOUNGE, line));
            }
        }
    }

    private synchronized void send(Frame frame) throws IOException {
        frame.write(output);
    }

    private void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private final class Listener implements Runnable {
        public void run() {
            try {
                while (running) {
                    Frame frame = Frame.read(input);
                    if (frame == null) {
                        break;
                    }
                    if (frame.type == Frame.LOUNGE || frame.type == Frame.NOTICE) {
                        System.out.println(frame.text());
                    } else if (frame.type == Frame.DIRECT) {
                        Frame.DirectPayload payload = Frame.directPayload(frame.payload);
                        System.out.println(payload.message);
                        send(Frame.ack(payload.id));
                    } else if (frame.type == Frame.ROOT) {
                        if (frame.payload.length > 0 && (frame.payload[0] & 0xFF) == Frame.ROOT_KICK) {
                            System.out.println(">> [SYSTEM NOTICE]: You have been kicked by the administrator.");
                            close();
                        }
                    }
                }
            } catch (IOException ignored) {
            } finally {
                close();
            }
        }
    }
}
