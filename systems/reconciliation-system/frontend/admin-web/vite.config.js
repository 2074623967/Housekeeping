import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const reconciliationApiTarget = process.env.RECONCILIATION_API_TARGET || "http://localhost:18150";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5180,
    proxy: {
      "/api": reconciliationApiTarget
    }
  }
});
