import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { crx } from "@crxjs/vite-plugin";
import manifest from "./manifest.json";
import path from "path";

export default defineConfig({
  plugins: [react(), crx({ manifest })],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
    // Ensure Kotlin/JS library's deps resolve from web/node_modules
    dedupe: ["@js-joda/core"],
  },
  build: {
    outDir: "dist",
    emptyOutDir: true,
    chunkSizeWarningLimit: 1024,
    rollupOptions: {
      input: {
        offscreen: path.resolve(__dirname, "src/offscreen/offscreen.html"),
      },
    },
  },
  optimizeDeps: {
    include: ["@js-joda/core"],
  },
});
