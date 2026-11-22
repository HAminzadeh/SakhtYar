package ir.sakhtyar.launcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Performs a real HTTPS request through the configured v2rayN HTTP/Mixed proxy. */
public final class ProxyHealthService {
    public record Result(String ip, String countryCode) { }
    private ProxyHealthService() { }

    public static Result check(int port, ObjectMapper mapper) throws Exception {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", port)))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://ipwho.is/"))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .GET()
            .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            throw new IOException("HTTPS proxy test failed via 127.0.0.1:" + port + ": " + e.getMessage(), e);
        }
        if (response.statusCode() != 200) throw new IOException("IP check HTTP " + response.statusCode());
        var data = mapper.readTree(response.body());
        if (!data.path("success").asBoolean(false)) throw new IOException("IP lookup did not succeed");
        String ip = data.path("ip").asText("");
        String country = data.path("country_code").asText("");
        if (ip.isBlank() || country.isBlank()) throw new IOException("IP lookup returned incomplete data");
        return new Result(ip, country);
    }
}
