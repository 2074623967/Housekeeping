import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const settlementApiTarget = process.env.HSP_SETTLEMENT_API_TARGET || "http://localhost:18130";

export default defineConfig({
  plugins: [vue()],
  cacheDir: "/private/tmp/hsp-settlement-admin-web-vite-cache",
  server: {
    port: 5274,
    proxy: {
      "/api": {
        target: settlementApiTarget,
        changeOrigin: true
      }
    }
  }
});
