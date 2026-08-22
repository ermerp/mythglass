/**
 * Die Gegenstelle zum Backend.
 *
 * Die Typen bilden die Records aus `dev.ermer.mythglass` ab. Das `type`-Feld einer Szene ist der
 * Angelpunkt: Kommt später ein Szenentyp dazu, wird er hier ergänzt und die Bühne bekommt einen
 * weiteren Fall — alles andere bleibt, wie es ist.
 */

export type Fit = "CONTAIN" | "COVER";

export type Scene =
  | { type: "blank" }
  | { type: "image"; assetId: string; fit: Fit };

export type SurfaceType = "VISUAL";

export interface SurfaceState {
  id: string;
  type: SurfaceType;
  displayName: string;
  connected: boolean;
  scene: Scene;
}

export interface StageState {
  surfaces: SurfaceState[];
}

export interface Asset {
  id: string;
  name: string;
  width: number;
  height: number;
}

export interface Folder {
  name: string;
  assets: Asset[];
}

export interface Library {
  folders: Folder[];
}

export const thumbnailUrl = (assetId: string) => `/api/assets/${encodeURIComponent(assetId)}/thumb`;

export const imageUrl = (assetId: string) => `/api/assets/${encodeURIComponent(assetId)}/full`;

/** Liest die Meldung aus der ProblemDetail-Antwort des Backends, damit die Oberfläche sie zeigen kann. */
async function ensureOk(response: Response): Promise<Response> {
  if (response.ok) {
    return response;
  }
  let detail = `${response.status} ${response.statusText}`;
  try {
    const problem = await response.json();
    if (problem?.detail) {
      detail = problem.detail;
    }
  } catch {
    // Keine ProblemDetail-Antwort — dann bleibt es beim Statuscode.
  }
  throw new Error(detail);
}

export async function fetchLibrary(): Promise<Library> {
  const response = await ensureOk(await fetch("/api/library"));
  return response.json();
}

export async function rescanLibrary(): Promise<number> {
  const response = await ensureOk(await fetch("/api/library/rescan", { method: "POST" }));
  const result = await response.json();
  return result.assetCount as number;
}

export async function showScene(surfaceId: string, scene: Scene): Promise<void> {
  await ensureOk(
    await fetch(`/api/surfaces/${encodeURIComponent(surfaceId)}/scene`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(scene),
    }),
  );
}

export async function blankSurface(surfaceId: string): Promise<void> {
  await ensureOk(
    await fetch(`/api/surfaces/${encodeURIComponent(surfaceId)}/blank`, { method: "POST" }),
  );
}
