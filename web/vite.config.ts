import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import { crx } from "@crxjs/vite-plugin";
import manifest from "./manifest.json";
import path from "path";
import fs from "fs";

const stripMarketingFromDist = (): Plugin => ({
  name: "strip-marketing-from-dist",
  apply: "build",
  closeBundle() {
    const target = path.resolve(__dirname, "dist/marketing");
    if (fs.existsSync(target)) fs.rmSync(target, { recursive: true, force: true });
  },
});

export default defineConfig(({ mode }) => {
  const marketing = process.env.VITE_MARKETING_MODE === "true" || mode === "marketing";
  return {
    plugins: marketing
      ? [react()]
      : [react(), crx({ manifest }), stripMarketingFromDist()],
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
          sidepanel: path.resolve(__dirname, "src/sidepanel/index.html"),
          offscreen: path.resolve(__dirname, "src/offscreen/offscreen.html"),
        },
      },
    },
    optimizeDeps: {
      include: ["@js-joda/core"],
    },
  };
});
