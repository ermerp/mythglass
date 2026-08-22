import { deflateSync } from "node:zlib";
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import path from "node:path";
import { CACHE_DIR, LIBRARY_DIR } from "./playwright.config";

/**
 * Legt vor dem Testlauf eine feste Bibliothek an.
 *
 * Echte Bilddateien statt Attrappen, weil der Ordner in dieser Anwendung das Datenmodell ist: Der
 * Scan liest Maße aus den Dateiköpfen, und die Vorschaubilder werden wirklich erzeugt. Eine Attrappe
 * würde genau den Teil wegabstrahieren, auf den es ankommt.
 */
export default function globalSetup() {
  rmSync(LIBRARY_DIR, { recursive: true, force: true });
  rmSync(CACHE_DIR, { recursive: true, force: true });

  writePng(path.join(LIBRARY_DIR, "NPCs", "Gorak der Wirt.png"), 1400, 1000, [200, 40, 40]);
  writePng(path.join(LIBRARY_DIR, "NPCs", "Elenya.png"), 900, 1500, [40, 180, 90]);
  writePng(path.join(LIBRARY_DIR, "Orte", "Taverne zum Krummen Ast.png"), 1920, 1080, [60, 60, 200]);
  writePng(path.join(LIBRARY_DIR, "Titelbild.png"), 1280, 720, [230, 180, 40]);
}

/** Schreibt ein PNG ohne Fremdbibliothek — es muss nur ein gültiges Bild mit klaren Maßen sein. */
function writePng(target: string, width: number, height: number, base: [number, number, number]) {
  mkdirSync(path.dirname(target), { recursive: true });

  const raw = Buffer.alloc(height * (1 + width * 3));
  let at = 0;
  for (let y = 0; y < height; y++) {
    raw[at++] = 0; // Filterbyte je Zeile
    for (let x = 0; x < width; x++) {
      raw[at++] = (base[0] + Math.floor((x * 255) / width)) % 256;
      raw[at++] = (base[1] + Math.floor((y * 255) / height)) % 256;
      raw[at++] = (base[2] + ((x ^ y) % 256)) % 256;
    }
  }

  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8; // 8 Bit je Kanal
  header[9] = 2; // Truecolor

  writeFileSync(target, Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]));
}

function chunk(tag: string, data: Buffer): Buffer {
  const body = Buffer.concat([Buffer.from(tag, "ascii"), data]);
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body), 0);
  return Buffer.concat([length, body, crc]);
}

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) {
    c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  }
  return c >>> 0;
});

function crc32(buffer: Buffer): number {
  let c = 0xffffffff;
  for (const byte of buffer) {
    c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8);
  }
  return (c ^ 0xffffffff) >>> 0;
}
