package dev.ermer.mythglass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulith;

/**
 * Mythglass — Präsentationswerkzeug für Pen-and-Paper-Runden vor Ort.
 *
 * <p>Die Anwendung projiziert Zustand auf benannte Ausgabeziele ("Surfaces"). In dieser Version gibt
 * es nur visuelle Surfaces, die Bilder zeigen; Karte und Sound docken später an derselben Naht an —
 * siehe {@link dev.ermer.mythglass.stage.Scene}.
 */
@Modulith
@SpringBootApplication
@ConfigurationPropertiesScan
public class MythglassApplication {

    public static void main(String[] args) {
        SpringApplication.run(MythglassApplication.class, args);
    }
}
