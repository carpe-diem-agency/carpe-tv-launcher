package agency.carpediem.tv.prototype;

import android.content.Context;
import android.os.Bundle;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Exercises the LAN form as a client without opening Android's real pairing flow. */
final class PairingLimitProbe {
    static void run(Context context, Bundle result) throws Exception {
        if (!context.getPackageName().endsWith(".publicpreview"))
            throw new IllegalStateException("Requires isolated preview package");
        try (LocalPairingServer server = new LocalPairingServer(code -> false, Locale.ENGLISH)) {
            URI uri = URI.create(server.address());
            require(response(uri, "GET").contains("<html lang=en>"), "English form unavailable");
            for (int attempt = 1; attempt <= 5; attempt++)
                require(status(uri, "POST") == 409, "Attempt " + attempt + " was not rejected");
            require(!reachable(uri), "Form remained available after five failures");
        }
        try (LocalPairingServer fresh = new LocalPairingServer(code -> false, Locale.forLanguageTag("uk"))) {
            String html = response(URI.create(fresh.address()), "GET");
            require(html.contains("<html lang=uk>") && html.contains("Введи шестизначний код"),
                    "Ukrainian form unavailable");
        }
        result.putString("pairingLimit", "5 rejected; old session closed; fresh session reachable");
    }

    private static boolean reachable(URI uri) {
        try { status(uri, "GET"); return true; }
        catch (Exception expected) { return false; }
    }

    private static int status(URI uri, String method) throws Exception {
        String response = response(uri, method);
        if (!response.startsWith("HTTP/1.1 ")) throw new AssertionError("No HTTP response");
        return Integer.parseInt(response.substring(9, 12));
    }

    private static String response(URI uri, String method) throws Exception {
        try (Socket socket = new Socket(uri.getHost(), uri.getPort())) {
            socket.setSoTimeout(3000);
            String body = "POST".equals(method) ? "code=000000" : "";
            String request = method + " " + uri.getRawPath() + " HTTP/1.1\r\n"
                    + "Host: " + uri.getHost() + ":" + uri.getPort() + "\r\n"
                    + "Content-Length: " + body.length() + "\r\nConnection: close\r\n\r\n" + body;
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (String line; (line = reader.readLine()) != null;) result.append(line).append('\n');
            return result.toString();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
