import type { Metadata } from "next";
import "./globals.css";

import { ThemeProvider } from "@/components/theme-provider";
import { SidebarConfigProvider } from "@/contexts/sidebar-context";
import { inter } from "@/lib/fonts";
import { Toaster } from "@/components/ui/sonner";

export const metadata: Metadata = {
  title: {
    default: "SafeResale Admin",
    template: "%s · SafeResale Admin",
  },
  description: "Verification Trust Engine — moderation, Q&A and operations console.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning className={`${inter.variable} antialiased`}>
      <body className={`${inter.className} min-h-svh bg-background font-sans antialiased`}>
        <ThemeProvider defaultTheme="system" storageKey="nextjs-ui-theme">
          <SidebarConfigProvider>
            {children}
            <Toaster position="bottom-right" richColors closeButton />
          </SidebarConfigProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
