import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const depositApiTarget = process.env.DEPOSIT_API_TARGET || "http://127.0.0.1:18160";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5181,
    proxy: {
      "/api": depositApiTarget
    }
  }
});
