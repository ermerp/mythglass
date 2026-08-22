package dev.ermer.mythglass.stage.internal;

import dev.ermer.mythglass.stage.SurfaceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Die Ausgabeziele werden konfiguriert, nicht dynamisch registriert.
 *
 * <p>Damit ist der spätere Kartenmonitor ein Eintrag in dieser Liste plus ein zweites Browserfenster
 * — und nicht ein Gerät, das sich unter einem beliebigen Namen anmelden und dabei vertippen kann.
 *
 * @param idleImage Anzeigename des Bildes, das eine Surface zeigt, solange nichts geschaltet ist.
 *     Findet sich kein Bild dieses Namens, zeigt die Anzeige ein eingebautes, dezentes Titelbild.
 */
@Validated
@ConfigurationProperties(prefix = "mythglass.stage")
public record StageProperties(
        @NotEmpty List<@Valid SurfaceDefinition> surfaces,
        @DefaultValue("15s") Duration heartbeatInterval,
        @DefaultValue("Titel") String idleImage) {

    public record SurfaceDefinition(
            @NotBlank String id,
            @NotNull SurfaceType type,
            @NotBlank String displayName) {}
}
