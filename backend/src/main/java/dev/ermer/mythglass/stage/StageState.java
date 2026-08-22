package dev.ermer.mythglass.stage;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Momentaufnahme aller Surfaces — die Nutzlast, die über SSE verteilt wird.
 *
 * <p>Es werden bewusst immer alle Surfaces vollständig übertragen statt einzelner Änderungen. Die
 * Nutzlast ist winzig, dafür entfällt jede Frage nach Reihenfolge und verpassten Ereignissen: Ein
 * Client, der irgendwann irgendein Ereignis empfängt, ist danach garantiert synchron.
 *
 * @param idleAssetId Bild, das eine Surface zeigt, solange nichts geschaltet ist; {@code null}, wenn
 *     die Bibliothek kein passendes enthält. Das gehört zum Zustand und nicht in die Szene: Es ist
 *     keine Anordnung des Spielleiters, sondern das, was mangels Anordnung zu sehen ist.
 */
public record StageState(List<SurfaceState> surfaces, @Nullable String idleAssetId) {}
