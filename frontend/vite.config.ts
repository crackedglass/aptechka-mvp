import {defineConfig, loadEnv} from 'vite';
import react from '@vitejs/plugin-react';

// Relative base keeps the build working under a GitHub Pages sub-path.
// In dev and preview the API is proxied to the backend, so the app calls it on
// its own origin: no CORS and no hardcoded backend URL in the bundle.
export default defineConfig(({mode}) => {
  const target = loadEnv(mode, process.cwd(), '').BACKEND_URL || 'http://localhost:8080';
  const proxy = {'/api': target, '/actuator': target};
  return {
    base: './',
    plugins: [react()],
    server: {proxy},
    preview: {proxy},
    build: {outDir: 'dist', emptyOutDir: true}
  };
});
