import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  cacheDir: "/private/tmp/hsp-accounting-admin-web-vite-cache",
  server: {
    port: 5181,
    proxy: {
      "/api": {
        target: "http://localhost:18110",
        changeOrigin: true
      }
    }
  }
});
