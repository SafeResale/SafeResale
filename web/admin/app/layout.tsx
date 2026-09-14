export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body style={{ fontFamily: "system-ui, sans-serif", margin: 0, background: "#f8fafc" }}>
        <header style={{ background: "#0f172a", color: "white", padding: "12px 24px", display: "flex", justifyContent: "space-between" }}>
          <strong>SafeResale Admin</strong>
          <span style={{ opacity: 0.7 }}>v1.0 • Verification Trust Engine</span>
        </header>
        <main style={{ maxWidth: 1100, margin: "24px auto", padding: "0 24px" }}>{children}</main>
      </body>
    </html>
  );
}
