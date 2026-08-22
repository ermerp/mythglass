package dev.ermer.mythglass.stage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

/**
 * Der Vertrag zum Frontend.
 *
 * <p>Das {@code type}-Feld ist die Stelle, an der die React-Seite entscheidet, welche Komponente sie
 * rendert. Wenn sich diese Namen unbemerkt ändern, zeigt der Monitor nichts mehr an — deshalb sind sie
 * hier festgenagelt.
 */
@JsonTest
class SceneJsonTest {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void blankSceneIsTaggedAsBlank() {
        assertThat(objectMapper.writeValueAsString(new BlankScene()))
                .isEqualTo("{\"type\":\"blank\"}");
    }

    @Test
    void imageSceneCarriesAssetAndFit() {
        String json = objectMapper.writeValueAsString(new ImageScene("abc123", Fit.COVER));

        assertThat(json).contains("\"type\":\"image\"", "\"assetId\":\"abc123\"", "\"fit\":\"COVER\"");
    }

    @Test
    void scenesSurviveARoundTrip() {
        Scene original = new ImageScene("abc123", Fit.CONTAIN);

        Scene restored = objectMapper.readValue(objectMapper.writeValueAsString(original), Scene.class);

        assertThat(restored).isEqualTo(original);
    }

    /** Die Steuerungsoberfläche darf "fit" weglassen; CONTAIN ist die sichere Vorgabe. */
    @Test
    void missingFitFallsBackToContain() {
        Scene restored = objectMapper.readValue("{\"type\":\"image\",\"assetId\":\"abc123\"}", Scene.class);

        assertThat(restored).isEqualTo(new ImageScene("abc123", Fit.CONTAIN));
    }
}
