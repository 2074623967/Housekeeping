import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const opsConfigApiTarget = process.env.OPS_CONFIG_API_TARGET || "http://127.0.0.1:18170";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5182,
    proxy: {
      "/api": opsConfigApiTarget
    }
  }
});
