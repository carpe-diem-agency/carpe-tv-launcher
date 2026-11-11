package agency.carpediem.tv.prototype;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;

/** One-time LAN form for entering the Android pairing code from a phone. */
final class LocalPairingServer implements Closeable {
    interface Receiver { boolean pair(String code); }
    private static final long LIFETIME_MS = 15 * 60 * 1000L;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private final ServerSocket listener;
    private final String token;
    private final String address;
    private final Receiver receiver;
    private final Thread thread;
    private volatile boolean closed;
    private int failedAttempts;

    LocalPairingServer(Receiver receiver) throws IOException {
        this.receiver = receiver;
        InetAddress local = wifiAddress();
        listener = new ServerSocket(0, 4, local);
        listener.setSoTimeout(1000);
        byte[] random = new byte[20];
        new SecureRandom().nextBytes(random);
        StringBuilder value = new StringBuilder(random.length * 2);
        for (byte b : random) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        token = value.toString();
        address = "http://" + local.getHostAddress() + ":" + listener.getLocalPort() + "/" + token;
        thread = new Thread(this::acceptLoop, "carpe-pairing-form");
        thread.setDaemon(true);
        thread.start();
    }

    String address() { return address; }

    private static InetAddress wifiAddress() throws IOException {
        InetAddress fallback = null;
        for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!network.isUp() || network.isLoopback()) continue;
            for (InetAddress address : Collections.list(network.getInetAddresses())) {
                if (!(address instanceof Inet4Address) || !address.isSiteLocalAddress()) continue;
                if (network.getName().startsWith("wlan") || network.getName().startsWith("eth")) return address;
                if (fallback == null) fallback = address;
            }
        }
        if (fallback != null) return fallback;
        throw new IOException("No local network address");
    }

    private void acceptLoop() {
        long deadline = android.os.SystemClock.elapsedRealtime() + LIFETIME_MS;
        while (!closed && android.os.SystemClock.elapsedRealtime() < deadline) {
            try (Socket client = listener.accept()) {
                // A stalled LAN client must not block the single pairing form for long.
                client.setSoTimeout(5000);
                serve(client);
            } catch (SocketTimeoutException ignored) {
                // Check the session deadline.
            } catch (IOException ignored) {
                if (closed) break;
            }
        }
        close();
    }

    private void serve(Socket client) throws IOException {
        InputStream input = client.getInputStream();
        String request = line(input, 2048);
        if (request == null) return;
        String method;
        if (request.equals("GET /" + token + " HTTP/1.1")) method = "GET";
        else if (request.equals("POST /" + token + " HTTP/1.1")) method = "POST";
        else { respond(client, 404, "Not found"); return; }
        String expectedHost = listener.getInetAddress().getHostAddress() + ":" + listener.getLocalPort();
        boolean hostOk = false;
        int length = -1;
        for (int n = 0; n < 32; n++) {
            String header = line(input, 2048);
            if (header == null) return;
            if (header.isEmpty()) break;
            if (header.regionMatches(true, 0, "Host: ", 0, 6)) hostOk = expectedHost.equals(header.substring(6).trim());
            if (header.regionMatches(true, 0, "Content-Length: ", 0, 16)) {
                try { length = Integer.parseInt(header.substring(16).trim()); }
                catch (NumberFormatException ignored) { length = -1; }
            }
        }
        if (!hostOk) { respond(client, 400, "Invalid host"); return; }
        if (method.equals("GET")) { respond(client, 200, page("Enter the six-digit code shown on your TV.")); return; }
        if (length != 11) { respond(client, 400, page("Enter exactly six digits.")); return; }
        byte[] body = new byte[length];
        for (int offset = 0; offset < body.length;) {
            int count = input.read(body, offset, body.length - offset);
            if (count < 0) return;
            offset += count;
        }
        String text = new String(body, StandardCharsets.US_ASCII);
        if (!text.matches("code=[0-9]{6}")) { respond(client, 400, page("Enter exactly six digits.")); return; }
        boolean success = receiver.pair(text.substring(5));
        if (!success) failedAttempts++;
        boolean exhausted = failedAttempts >= MAX_FAILED_ATTEMPTS;
        respond(client, success ? 200 : 409, page(success
                ? "Paired. You can return to your TV."
                : exhausted ? "Too many attempts. Restart pairing on your TV to try again."
                : "Pairing failed or the TV code has expired. Check the code and try again."));
        if (success || exhausted) close();
    }

    private static String line(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int n = 0; n < limit; n++) {
            int b = in.read();
            if (b == -1) return null;
            if (b == '\n') return new String(out.toByteArray(), StandardCharsets.US_ASCII).replace("\r", "");
            out.write(b);
        }
        throw new IOException("Header too long");
    }

    private static String page(String message) {
        return "<!doctype html><html lang=en><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<title>Carpe TV pairing</title><style>body{font:18px system-ui;background:#101b2c;color:#fff;"
                + "max-width:32rem;margin:12vh auto;padding:1.5rem}input,button{font:inherit;padding:.8rem;border-radius:.7rem}"
                + "input{width:11rem}button{background:#67a8ff;color:#061224;border:0;margin-left:.4rem}</style>"
                + "<h1>Carpe TV</h1><p>" + message + "</p><form method=post><input name=code inputmode=numeric "
                + "autocomplete=off pattern='[0-9]{6}' maxlength=6 required aria-label='Pairing code'>"
                + "<button type=submit>Pair</button></form></html>";
    }

    private static void respond(Socket client, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String label = status == 200 ? "OK" : status == 404 ? "Not Found" : status == 409 ? "Conflict" : "Bad Request";
        OutputStream out = client.getOutputStream();
        out.write(("HTTP/1.1 " + status + " " + label + "\r\nContent-Type: text/html; charset=utf-8\r\n"
                + "Cache-Control: no-store, max-age=0\r\nReferrer-Policy: no-referrer\r\n"
                + "X-Content-Type-Options: nosniff\r\nConnection: close\r\nContent-Length: "
                + bytes.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    @Override public void close() {
        closed = true;
        try { listener.close(); } catch (IOException ignored) { }
    }
}
