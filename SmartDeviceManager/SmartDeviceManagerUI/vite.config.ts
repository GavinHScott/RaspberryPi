import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@salt-ds/core": path.resolve(__dirname, "node_modules/@salt-ds/core"),
      "@salt-ds/theme": path.resolve(__dirname, "node_modules/@salt-ds/theme"),
      react: path.resolve(__dirname, "node_modules/react"),
      "react-dom": path.resolve(__dirname, "node_modules/react-dom"),
    },
  },
  server: {
    fs: {
      allow: ["../.."],
    },
    host: "0.0.0.0",
    port: 9091,
  },
  preview: {
    host: "0.0.0.0",
    port: 9091,
  },
});
