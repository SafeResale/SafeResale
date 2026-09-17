/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    return [
      // Admin panel -> backend, SAME ORIGIN. The browser never sees :8000,
      // so no CORS and no token-loss: Authorization: Bearer rides along.
      { source: "/admin/:path*", destination: "http://127.0.0.1:8000/admin/:path*" },
      { source: "/queue/:path*", destination: "http://127.0.0.1:8000/listings/flagged" },
      { source: "/auth/:path*", destination: "http://127.0.0.1:8000/auth/:path*" },
      { source: "/uploads/:path*", destination: "http://127.0.0.1:8000/uploads/:path*" },
      { source: "/health", destination: "http://127.0.0.1:8000/health" },
    ];
  },
};

export default nextConfig;
