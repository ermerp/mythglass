import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "dist",
    emptyOutDir: true,
  },
  server: {
    // Für `npm run dev`: Das Backend läuft daneben auf 8080. Im gebauten Stand liefert Spring Boot
    // die Oberfläche selbst aus, dann gibt es diesen Umweg nicht mehr.
    proxy: {
      "/api": { target: "http://localhost:8080", changeOrigin: true },
    },
  },
});
