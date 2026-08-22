package dev.ermer.mythglass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Führt die beiden Module an einer echten Bibliothek aus Dateien zusammen.
 *
 * <p>Die Bibliothek wird als Ordner mit echten Bilddateien angelegt statt gemockt — der Ordner *ist*
 * das Datenmodell dieser Anwendung, ein Mock würde genau das wegabstrahieren, worauf es ankommt.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MythglassApiTest {

    private static final Path LIBRARY = Path.of("build", "test-library").toAbsolutePath().normalize();
    private static final Path CACHE = Path.of("build", "test-cache").toAbsolutePath().normalize();

    static {
        // Bewusst im statischen Initialisierer: Die Dateien müssen liegen, bevor der Spring-Kontext
        // startet und die Bibliothek beim Hochfahren einliest.
        deleteRecursively(LIBRARY);
        deleteRecursively(CACHE);
        writePng(LIBRARY.resolve("NPCs/Gorak der Wirt.png"), 1200, 800);
        writePng(LIBRARY.resolve("NPCs/Elenya.png"), 900, 1600);
        writePng(LIBRARY.resolve("Orte/Taverne.png"), 1920, 1080);
        writePng(LIBRARY.resolve("Titelbild.png"), 800, 600);
        writePng(LIBRARY.resolve(".versteckt/Geheim.png"), 100, 100);
        // Gleicher Anzeigename wie das Ruhebild, aber in einem Unterordner: Der Wurzelordner hat Vorrang.
        writePng(LIBRARY.resolve("Orte/Titel.png"), 300, 200);
        writePng(LIBRARY.resolve("Titel.png"), 640, 360);
        write(LIBRARY.resolve("Notizen.txt"), "kein Bild");
    }

    @DynamicPropertySource
    static void libraryLocation(DynamicPropertyRegistry registry) {
        registry.add("mythglass.library.path", () -> LIBRARY);
        registry.add("mythglass.library.cache", () -> CACHE);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    dev.ermer.mythglass.library.LibraryService library;

    @BeforeEach
    void resetSurface() throws Exception {
        mockMvc.perform(post("/api/surfaces/main/blank")).andExpect(status().isOk());
    }

    @Test
    void libraryIsGroupedByFolder() throws Exception {
        mockMvc.perform(get("/api/library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders.length()").value(3))
                .andExpect(jsonPath("$.folders[0].name").value("Allgemein"))
                .andExpect(jsonPath("$.folders[0].assets[0].name").value("Titel"))
                .andExpect(jsonPath("$.folders[0].assets[1].name").value("Titelbild"))
                .andExpect(jsonPath("$.folders[1].name").value("NPCs"))
                .andExpect(jsonPath("$.folders[1].assets.length()").value(2))
                // Sortiert nach Namen, nicht nach Fundreihenfolge im Dateisystem.
                .andExpect(jsonPath("$.folders[1].assets[0].name").value("Elenya"))
                .andExpect(jsonPath("$.folders[1].assets[1].name").value("Gorak der Wirt"))
                .andExpect(jsonPath("$.folders[2].name").value("Orte"))
                .andExpect(jsonPath("$.folders[2].assets.length()").value(2));
    }

    @Test
    void dimensionsAreReadFromTheFile() throws Exception {
        mockMvc.perform(get("/api/library"))
                .andExpect(jsonPath("$.folders[1].assets[0].width").value(900))
                .andExpect(jsonPath("$.folders[1].assets[0].height").value(1600));
    }

    @Test
    void hiddenFoldersAndNonImagesAreIgnored() throws Exception {
        String body = mockMvc.perform(get("/api/library")).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Geheim", "Notizen", "versteckt");
    }

    @Test
    void surfaceStartsBlank() throws Exception {
        mockMvc.perform(get("/api/surfaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("main"))
                .andExpect(jsonPath("$[0].displayName").value("Spielleiter-Monitor"))
                .andExpect(jsonPath("$[0].connected").value(false))
                .andExpect(jsonPath("$[0].scene.type").value("blank"));
    }

    @Test
    void showingAnImagePutsItOnTheSurface() throws Exception {
        String assetId = anyAssetId();

        mockMvc.perform(put("/api/surfaces/main/scene")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"image\",\"assetId\":\"" + assetId + "\",\"fit\":\"COVER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scene.type").value("image"))
                .andExpect(jsonPath("$[0].scene.assetId").value(assetId))
                .andExpect(jsonPath("$[0].scene.fit").value("COVER"));

        mockMvc.perform(get("/api/surfaces"))
                .andExpect(jsonPath("$[0].scene.assetId").value(assetId));
    }

    @Test
    void blankingClearsTheSurface() throws Exception {
        mockMvc.perform(put("/api/surfaces/main/scene")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"image\",\"assetId\":\"" + anyAssetId() + "\"}"));

        mockMvc.perform(post("/api/surfaces/main/blank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scene.type").value("blank"));
    }

    @Test
    void unknownAssetIsRejectedInsteadOfShowingNothing() throws Exception {
        mockMvc.perform(put("/api/surfaces/main/scene")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"image\",\"assetId\":\"gibtesnicht\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Bild nicht gefunden"));

        mockMvc.perform(get("/api/surfaces")).andExpect(jsonPath("$[0].scene.type").value("blank"));
    }

    @Test
    void unknownSurfaceIsRejected() throws Exception {
        mockMvc.perform(post("/api/surfaces/tisch/blank"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Ausgabeziel nicht gefunden"));
    }

    @Test
    void thumbnailIsServedAsJpegAndIsSmallerThanTheOriginal() throws Exception {
        String assetId = anyAssetId();

        byte[] thumbnail = mockMvc.perform(get("/api/assets/{id}/thumb", assetId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                .andReturn().getResponse().getContentAsByteArray();

        byte[] full = mockMvc.perform(get("/api/assets/{id}/full", assetId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(thumbnail.length).isLessThan(full.length);
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(thumbnail)).getWidth()).isLessThanOrEqualTo(480);
    }

    /** Ein erneut gezeigtes Bild soll nicht noch einmal über das WLAN wandern. */
    @Test
    void unchangedImageIsAnsweredWithNotModified() throws Exception {
        String assetId = anyAssetId();
        String etag = mockMvc.perform(get("/api/assets/{id}/full", assetId))
                .andExpect(header().exists("ETag"))
                .andReturn().getResponse().getHeader("ETag");

        mockMvc.perform(get("/api/assets/{id}/full", assetId).header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void unknownAssetIdIsNotFound() throws Exception {
        mockMvc.perform(get("/api/assets/{id}/full", "../../etc/passwd")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/assets/{id}/full", "nichtvorhanden")).andExpect(status().isNotFound());
    }

    /**
     * Der Fall, der am Spieltisch sonst verwirrt: Ein Bild wird gezeigt, danach verschwindet die Datei.
     * Statt eines toten Verweises soll der Monitor sauber schwarz werden.
     */
    @Test
    void rescanBlanksSurfacesWhoseImageDisappeared() throws Exception {
        Path temporaryImage = LIBRARY.resolve("Orte/Ruine.png");
        writePng(temporaryImage, 640, 480);
        mockMvc.perform(post("/api/library/rescan")).andExpect(status().isOk());

        String assetId = assetIdOf("Ruine");
        mockMvc.perform(put("/api/surfaces/main/scene")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"image\",\"assetId\":\"" + assetId + "\"}"))
                .andExpect(jsonPath("$[0].scene.type").value("image"));

        Files.delete(temporaryImage);
        mockMvc.perform(post("/api/library/rescan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetCount").value(6));

        mockMvc.perform(get("/api/surfaces")).andExpect(jsonPath("$[0].scene.type").value("blank"));
    }

    /**
     * Das Ruhebild wird über den Dateinamen gefunden, nicht über eine ID — der Spielleiter soll
     * einfach eine Datei ablegen können. Bei gleichem Namen gewinnt der Wurzelordner.
     */
    @Test
    void idleImageIsResolvedByFileNameWithRootFolderWinning() throws Exception {
        String rootTitle = assetIdIn("Allgemein", "Titel");
        String nestedTitle = assetIdIn("Orte", "Titel");
        assertThat(rootTitle).isNotEqualTo(nestedTitle);

        assertThat(library.findByDisplayName("Titel")).hasValueSatisfying(asset ->
                assertThat(asset.id()).isEqualTo(rootTitle));
        assertThat(library.findByDisplayName("titel")).as("Groß- und Kleinschreibung egal")
                .hasValueSatisfying(asset -> assertThat(asset.id()).isEqualTo(rootTitle));
        assertThat(library.findByDisplayName("gibtesnicht")).isEmpty();
    }

    private String assetIdIn(String folder, String displayName) throws Exception {
        String body = mockMvc.perform(get("/api/library")).andReturn().getResponse().getContentAsString();
        int folderAt = body.indexOf("\"name\":\"" + folder + "\"");
        assertThat(folderAt).as("Ordner '%s'", folder).isGreaterThan(-1);
        int nameAt = body.indexOf("\"name\":\"" + displayName + "\"", folderAt);
        assertThat(nameAt).as("Bild '%s' in '%s'", displayName, folder).isGreaterThan(-1);
        int idAt = body.lastIndexOf("\"id\":\"", nameAt) + "\"id\":\"".length();
        return body.substring(idAt, body.indexOf('"', idAt));
    }

    private String anyAssetId() throws Exception {
        return assetIdOf("Gorak der Wirt");
    }

    private String assetIdOf(String displayName) throws Exception {
        String body = mockMvc.perform(get("/api/library")).andReturn().getResponse().getContentAsString();
        int nameAt = body.indexOf("\"name\":\"" + displayName + "\"");
        assertThat(nameAt).as("Bild '%s' in der Bibliothek", displayName).isGreaterThan(-1);
        int idAt = body.lastIndexOf("\"id\":\"", nameAt) + "\"id\":\"".length();
        return body.substring(idAt, body.indexOf('"', idAt));
    }

    /**
     * Erzeugt ein Bild mit Verlauf und Rauschen. Ein einfarbiges Testbild wäre unrealistisch: Es
     * komprimiert so gut, dass ein daraus erzeugtes Thumbnail größer ausfällt als das Original.
     */
    private static void writePng(Path target, int width, int height) {
        try {
            Files.createDirectories(target.getParent());
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Random random = new Random(width * 31L + height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int red = (x * 255 / width + random.nextInt(40)) & 0xFF;
                    int green = (y * 255 / height + random.nextInt(40)) & 0xFF;
                    int blue = random.nextInt(256);
                    image.setRGB(x, y, (red << 16) | (green << 8) | blue);
                }
            }
            ImageIO.write(image, "png", target.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
