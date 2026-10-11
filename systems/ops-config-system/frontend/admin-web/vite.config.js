import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const opsConfigApiTarget = process.env.OPS_CONFIG_API_TARGET || "http://localhost:18170";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5182,
    proxy: {
      "/api": opsConfigApiTarget
    }
  }
});
