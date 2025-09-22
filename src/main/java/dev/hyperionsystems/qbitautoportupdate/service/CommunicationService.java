package dev.hyperionsystems.qbitautoportupdate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hyperionsystems.qbitautoportupdate.model.Preferences;
import dev.hyperionsystems.qbitautoportupdate.model.Torrent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

@Service
@Slf4j
public class CommunicationService {

    private final HttpClient client;
    private final URI baseUri;
    private final ObjectMapper objectMapper;


    private volatile String username;
    private volatile String password;


    public CommunicationService(
            @Value("${api.base-url}") String baseUrl,
            ObjectMapper objectMapper,
            @Value("${api.username:admin}") String defaultUsername,
            @Value("${api.password:}") String defaultPassword
    ) {
        this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_2)
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)) // Cookies automatisch verwalten
                .build();
        this.baseUri = URI.create(baseUrl);
        this.username = defaultUsername;
        this.password = defaultPassword;
    }

    public void processNewPort(int port) {
        this.login();
        Preferences preferences = this.getPreferences();
        if (preferences.getListenPort() == port) {
            log.info("Port {} is already set", port);
            return;
        }
        this.setPort(port);
        log.info("Port {} set", port);
        ArrayList<Torrent> torrents = this.getActiveTorrents();
        this.toggleTorrents(torrents, "stop");
        log.info("Stopping {} torrents", torrents.size());
        try{
            TimeUnit.SECONDS.sleep(3);
        }catch (InterruptedException ignore){}
        log.info("Starting {} torrents", torrents.size());
        this.toggleTorrents(torrents, "start");
        log.info("Process finished");
    }

    public void login() {
        doLogin(this.username, this.password);
    }

    public void login(String username, String password) {
        if (username != null) {
            this.username = username;
        }
        if (password != null) {
            this.password = password;
        }
        doLogin(this.username, this.password);
    }

    private void doLogin(String username, String password) {
        String form = "username=" + URLEncoder.encode(username, UTF_8)
                + "&password=" + URLEncoder.encode(password, UTF_8);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/v2/auth/login"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form, UTF_8))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                throw new RuntimeException("Login failed: HTTP " + code + " Body=" + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Login-Request failed", e);
        }
    }

    private Preferences getPreferences() {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/v2/app/preferences"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                throw new RuntimeException("GET /preferences failed: HTTP " + code + " Body=" + response.body());
            }
            return objectMapper.readValue(response.body(), Preferences.class);
        } catch (Exception e) {
            throw new RuntimeException("Abruf/Parsing der Preferences failed", e);
        }

    }

    private void setPort(int port) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/v2/app/setPreferences"))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("json={\"listen_port\":" + port + "}", UTF_8))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                throw new RuntimeException("POST /setPreferences failed: HTTP " + code + " Body=" + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("POST /setPreferences failed", e);
        }
    }

    private ArrayList<Torrent> getActiveTorrents() {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/v2/torrents/info?filter=running"))
                .timeout(Duration.ofMinutes(1))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                throw new RuntimeException("GET /torrents failed: HTTP " + code + " Body=" + response.body());
            }
            return objectMapper.readValue(response.body(), objectMapper.getTypeFactory().constructCollectionType(ArrayList.class, Torrent.class));
        } catch (Exception e) {
            throw new RuntimeException("torrent request failed", e);
        }
    }

    private void toggleTorrents(ArrayList<Torrent> torrents, String action) {
        if (torrents == null || torrents.isEmpty()) {
            return;
        }
        String joinedHashes = torrents.stream()
                .map(Torrent::getHash)
                .collect(java.util.stream.Collectors.joining("|"));
        String payload = "hashes=" + java.net.URLEncoder.encode(joinedHashes, UTF_8);

        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/v2/torrents/" + action))
                .timeout(java.time.Duration.ofMinutes(1))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(payload, UTF_8))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                throw new RuntimeException(String.format(
                        "POST %s failed: HTTP %d Body=%s",
                        action, code, response.body()));
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("POST %s failed", action), e);

        }
    }


}
