package dev.ermer.mythglass.stage;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Was auf einer Surface gerade dargestellt wird.
 *
 * <p>Dies ist die Naht, an der die Anwendung wächst. Ein neuer Inhaltstyp — eine Karte auf dem
 * Tischmonitor, ein Klang auf einer Audio-Surface — ist ein weiterer Record hier plus eine
 * Darstellungskomponente im Frontend, die auf das {@code type}-Feld matched. Am Zustandsmodell, an der
 * Verteilung über SSE und an der Steuerungs-API ändert sich dafür nichts.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = BlankScene.class, name = "blank"),
    @JsonSubTypes.Type(value = ImageScene.class, name = "image")
})
public sealed interface Scene permits BlankScene, ImageScene {

    /** Der sichere Ausgangszustand: Der Monitor zeigt nichts. */
    Scene BLANK = new BlankScene();
}
