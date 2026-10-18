/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    return [
      // Admin panel -> backend, SAME ORIGIN. The browser never sees :8000,
      // so no CORS and no token-loss: Authorization: Bearer rides along.
      //
      // IMPORTANT: every API call goes through the `/api` prefix (see lib/api.ts).
      // `rewrites()` are `afterFiles` rewrites, so a filesystem route always wins.
      // Bare paths such as `/inspections` or `/listings` collide with App Router
      // page routes of the same name, the page's HTML is returned instead of JSON,
      // and the client silently renders an empty screen.
      { source: "/api/:path*", destination: "http://127.0.0.1:8000/:path*" },

      // Raw <img src> / <a href> URLs — not routed through lib/api.ts.
      { source: "/uploads/:path*", destination: "http://127.0.0.1:8000/uploads/:path*" },
    ];
  },
};

export default nextConfig;