import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// El panel habla con Django (puerto 8000), que ahora sirve /api/admin/*.
// En desarrollo Vite hace de proxy para no depender de CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8000",
        changeOrigin: true,
      },
    },
  },
});
