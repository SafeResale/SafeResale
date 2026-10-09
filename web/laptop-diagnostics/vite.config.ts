import path from 'path';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'vite';

const port = Number(process.env.PORT ?? 5174);

if (Number.isNaN(port) || port <= 0) {
  throw new Error(`Invalid PORT value: "${process.env.PORT}"`);
}

const basePath = process.env.BASE_PATH ?? '/';
const measurementSizes = new Set(['100000', '500000', '1000000', '2000000', '5000000', '10000000', '20000000']);

function rewriteMeasurementPath(requestPath: string) {
  const requestUrl = new URL(requestPath, 'http://localhost');
  const measurement = requestUrl.pathname.split('/').pop() ?? '';
  const bytes = measurement === 'latency' ? '0' : measurementSizes.has(measurement) ? measurement : null;
  if (bytes === null) return '/__invalid-network-measurement';
  requestUrl.pathname = '/__down';
  requestUrl.searchParams.delete('bytes');
  requestUrl.searchParams.set('bytes', bytes);
  return `${requestUrl.pathname}?${requestUrl.searchParams.toString()}`;
}

export default defineConfig({
  base: basePath,
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, 'src'),
    },
    dedupe: ['react', 'react-dom'],
  },
  root: path.resolve(import.meta.dirname),
  build: {
    outDir: path.resolve(import.meta.dirname, 'dist'),
    emptyOutDir: true,
  },
  server: {
    port,
    strictPort: true,
    proxy: {
      '/network-measurement': {
        target: 'https://speed.cloudflare.com',
        changeOrigin: true,
        rewrite: rewriteMeasurementPath,
      },
    },
    fs: {
      strict: true,
    },
  },
  preview: { port },
});
