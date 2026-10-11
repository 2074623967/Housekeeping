import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const clearingApiTarget = process.env.HSP_CLEARING_API_TARGET || "http://localhost:18120";

export default defineConfig({
  plugins: [vue()],
  cacheDir: "/private/tmp/hsp-clearing-admin-web-vite-cache",
  server: {
    port: 5273,
    proxy: {
      "/api": {
        target: clearingApiTarget,
        changeOrigin: true
      }
    }
  }
});
