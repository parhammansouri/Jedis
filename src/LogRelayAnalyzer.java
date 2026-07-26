import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class LogRelayAnalyzer {
    private static final int LISTEN_PORT = 38291;
    private static final int UPSTREAM_PORT = 38292;

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(LISTEN_PORT);
        Socket client = serverSocket.accept();
        try {
            new LogRelayAnalyzer().handle(client);
        } finally {
            client.close();
            serverSocket.close();
        }
    }

    private void handle(Socket client) throws IOException {
        Socket upstream = new Socket("127.0.0.1", UPSTREAM_PORT);
        try {
            BufferedReader clientIn = new BufferedReader(new InputStreamReader(client.getInputStream()));
            BufferedWriter clientOut = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
            BufferedReader upstreamIn = new BufferedReader(new InputStreamReader(upstream.getInputStream()));
            BufferedWriter upstreamOut = new BufferedWriter(new OutputStreamWriter(upstream.getOutputStream()));

            Stats stats = new Stats();
            String line;
            while ((line = clientIn.readLine()) != null) {
                upstreamOut.write(line);
                upstreamOut.newLine();
                upstreamOut.flush();
                if ("END".equals(line)) {
                    break;
                }
                stats.accept(line);
            }

            int upstreamLines = 0;
            String upstreamCheck = "0";
            while ((line = upstreamIn.readLine()) != null) {
                if (line.startsWith("UPSTREAM_LINES ")) {
                    upstreamLines = parseInt(line.substring("UPSTREAM_LINES ".length()), 0);
                } else if (line.startsWith("UPSTREAM_CHECK ")) {
                    upstreamCheck = line.substring("UPSTREAM_CHECK ".length()).trim();
                } else if ("UPSTREAM_END".equals(line)) {
                    break;
                }
            }

            write(clientOut, "LINES " + stats.lines);
            write(clientOut, "INFO " + stats.info);
            write(clientOut, "WARN " + stats.warn);
            write(clientOut, "ERROR " + stats.error);
            write(clientOut, "LONGEST " + stats.longest);
            write(clientOut, "ERROR_BURST " + stats.bestErrorBurst);
            write(clientOut, "ERROR_BURST_START " + stats.bestErrorBurstStart);
            write(clientOut, "UPSTREAM_LINES " + upstreamLines);
            write(clientOut, "UPSTREAM_CHECK " + upstreamCheck);
            write(clientOut, "END");
        } finally {
            upstream.close();
        }
    }

    private static void write(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final class Stats {
        int lines;
        int info;
        int warn;
        int error;
        int longest;
        int currentErrorBurst;
        int currentErrorBurstStart;
        int bestErrorBurst;
        int bestErrorBurstStart;

        void accept(String line) {
            lines++;
            if (line.length() > longest) {
                longest = line.length();
            }
            String level = levelOf(line);
            if ("INFO".equals(level)) {
                info++;
                closeErrorBurst();
            } else if ("WARN".equals(level)) {
                warn++;
                closeErrorBurst();
            } else if ("ERROR".equals(level)) {
                error++;
                if (currentErrorBurst == 0) {
                    currentErrorBurstStart = lines;
                }
                currentErrorBurst++;
                if (currentErrorBurst > bestErrorBurst) {
                    bestErrorBurst = currentErrorBurst;
                    bestErrorBurstStart = currentErrorBurstStart;
                }
            } else {
                closeErrorBurst();
            }
        }

        private void closeErrorBurst() {
            currentErrorBurst = 0;
            currentErrorBurstStart = 0;
        }

        private String levelOf(String line) {
            String[] parts = line.split("\\s+", 4);
            if (parts.length < 2) {
                return "";
            }
            return parts[1];
        }
    }
}
