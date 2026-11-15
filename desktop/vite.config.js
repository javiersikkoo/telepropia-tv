import { defineConfig } from 'vite';

// Puerto fijo: Tauri carga la interfaz de desarrollo desde aquí.
export default defineConfig({
  clearScreen: false,
  server: { port: 1420, strictPort: true },
  build: { target: ['es2021', 'chrome105', 'safari15'], outDir: 'dist', chunkSizeWarningLimit: 1024 },
});
