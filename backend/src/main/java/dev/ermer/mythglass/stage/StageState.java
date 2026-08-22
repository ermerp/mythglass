package dev.ermer.mythglass.stage;

import java.util.List;

/**
 * Momentaufnahme aller Surfaces — die Nutzlast, die über SSE verteilt wird.
 *
 * <p>Es werden bewusst immer alle Surfaces vollständig übertragen statt einzelner Änderungen. Die
 * Nutzlast ist winzig, dafür entfällt jede Frage nach Reihenfolge und verpassten Ereignissen: Ein
 * Client, der irgendwann irgendein Ereignis empfängt, ist danach garantiert synchron.
 */
public record StageState(List<SurfaceState> surfaces) {}
