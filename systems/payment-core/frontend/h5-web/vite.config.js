import path from "node:path";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  cacheDir: "/private/tmp/hsp-h5-web-vite-cache",
  resolve: {
    alias: {
      vue: path.resolve(__dirname, "node_modules/vue"),
      "vue-router": path.resolve(__dirname, "node_modules/vue-router")
    }
  },
  server: {
    port: 5176,
    fs: {
      allow: [path.resolve(__dirname, "..")]
    },
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  }
});
