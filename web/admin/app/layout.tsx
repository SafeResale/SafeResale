import type { Metadata } from "next";
import { Providers } from "@/components/providers";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "SafeResale Admin",
    template: "%s · SafeResale Admin",
  },
  description: "Verification Trust Engine — moderation, Q&A and operations console.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="min-h-svh bg-background font-sans antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}