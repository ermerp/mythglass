package dev.ermer.mythglass.stage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Prüft den Kanal, auf dem am Spieltisch alles steht.
 *
 * <p>Getestet wird nicht nur, dass Änderungen ankommen, sondern die eigentliche Eigenschaft des
 * Entwurfs: Wer sich verbindet, bekommt sofort den vollständigen Zustand. Genau das macht aus einem
 * Verbindungsabriss einen Selbstheilungsvorgang statt eines Fehlerfalls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StageEventStreamTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("mythglass.library.path", () -> Path.of("build", "empty-library").toAbsolutePath());
        registry.add("mythglass.library.cache", () -> Path.of("build", "empty-cache").toAbsolutePath());
        // Ein getrennter Client fällt erst durch einen fehlgeschlagenen Schreibversuch auf. Im Betrieb
        // darf das 15 Sekunden dauern, im Test soll es sofort passieren.
        registry.add("mythglass.stage.heartbeat-interval", () -> "100ms");
    }

    @LocalServerPort
    int port;

    private HttpClient client;

    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    @Test
    void observerSeesDisplayConnectShowAndDisconnect() throws Exception {
        try (EventStream observer = open(null)) {
            assertThat(observer.next())
                    .as("Beim Verbinden kommt sofort der vollständige Zustand")
                    .contains("\"id\":\"main\"")
                    .contains("\"connected\":false")
                    .contains("\"type\":\"blank\"");

            try (EventStream display = open("main")) {
                assertThat(display.next())
                        .as("Auch das Anzeigegerät bekommt den Zustand ohne Nachfrage")
                        .contains("\"type\":\"blank\"");

                assertThat(observer.next())
                        .as("Die Steuerung erfährt, dass der Monitor jetzt zuhört")
                        .contains("\"connected\":true");

                blankMainSurface();

                assertThat(display.next()).contains("\"type\":\"blank\"");
                assertThat(observer.next()).contains("\"type\":\"blank\"");
            }

            assertThat(observer.next())
                    .as("Nach dem Trennen des Monitors meldet die Steuerung ihn als abwesend")
                    .contains("\"connected\":false");
        }
    }

    /** Ein zweiter Beobachter, der später dazukommt, sieht denselben Zustand — ohne Nachholmechanik. */
    @Test
    void lateSubscriberIsHandedTheCurrentState() throws Exception {
        try (EventStream display = open("main")) {
            display.next();

            try (EventStream latecomer = open(null)) {
                assertThat(latecomer.next()).contains("\"connected\":true");
            }
        }
    }

    private void blankMainSurface() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(uri("/api/surfaces/main/blank")).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private EventStream open(String surfaceId) throws Exception {
        String path = surfaceId == null ? "/api/events" : "/api/events?surface=" + surfaceId;
        HttpResponse<InputStream> response = client.send(
                HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        return new EventStream(response.body());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** Liest einen SSE-Strom im Hintergrund und stellt die {@code data:}-Zeilen zum Abholen bereit. */
    private static final class EventStream implements AutoCloseable {

        private final InputStream source;
        private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
        private final Thread reader;

        EventStream(InputStream source) {
            this.source = source;
            this.reader = Thread.ofVirtual().start(() -> {
                try (BufferedReader lines = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = lines.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            events.add(line.substring("data:".length()).trim());
                        }
                    }
                } catch (IOException expectedOnClose) {
                    // Beim Schließen der Verbindung erwartet.
                }
            });
        }

        String next() throws InterruptedException {
            String event = events.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(event).as("Ereignis innerhalb von %s", TIMEOUT).isNotNull();
            return event;
        }

        @Override
        public void close() throws Exception {
            source.close();
            reader.interrupt();
        }
    }
}
