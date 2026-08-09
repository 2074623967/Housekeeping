import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5179,
    proxy: {
      "/api": "http://127.0.0.1:18140"
    }
  }
});

