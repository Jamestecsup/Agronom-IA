import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// La web de usuario habla con Django (puerto 8000). El proxy evita CORS en
// desarrollo y mantiene las rutas /agronomia-api iguales a las del móvil.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      "/agronomia-api": {
        target: "http://127.0.0.1:8000",
        changeOrigin: true,
      },
    },
  },
});
