package dev.ermer.mythglass.library.internal;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param path Wurzel der Bilderbibliothek. Im Container read-only eingehängt.
 * @param cache Ablage für generierte Thumbnails. Darf jederzeit gelöscht werden.
 */
@ConfigurationProperties(prefix = "mythglass.library")
public record LibraryProperties(Path path, Path cache) {}
