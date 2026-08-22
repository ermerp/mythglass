import { useEffect, useState } from "react";
import type { StageState } from "./api";

export interface StageConnection {
  /** Der zuletzt empfangene Zustand; `null`, solange noch keiner angekommen ist. */
  state: StageState | null;
  /** Ob die Verbindung zum Server gerade steht. */
  online: boolean;
}

/**
 * Hört auf den Zustandsstrom des Servers.
 *
 * Hier liegt bewusst kein eigener Zustand und keine Nachholmechanik: Der Server schickt bei jeder
 * Verbindung — auch bei jeder Wiederverbindung — den vollständigen Zustand, und der Wiederverbindungs-
 * versuch selbst steckt im EventSource des Browsers. Damit ist ein gesperrtes Handy oder ein kurz
 * weggebrochenes WLAN kein Sonderfall, um den sich hier jemand kümmern müsste.
 *
 * @param surfaceId gesetzt, wenn sich dieses Gerät als Anzeigefläche meldet; sonst nur zuhören
 */
export function useStageState(surfaceId?: string): StageConnection {
  const [state, setState] = useState<StageState | null>(null);
  const [online, setOnline] = useState(false);

  useEffect(() => {
    const url = surfaceId
      ? `/api/events?surface=${encodeURIComponent(surfaceId)}`
      : "/api/events";
    const source = new EventSource(url);

    source.addEventListener("state", (event) => {
      setState(JSON.parse((event as MessageEvent<string>).data) as StageState);
      setOnline(true);
    });
    source.addEventListener("open", () => setOnline(true));
    source.addEventListener("error", () => setOnline(false));

    return () => source.close();
  }, [surfaceId]);

  return { state, online };
}
