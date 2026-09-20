"use client";

import { ThemeProvider } from "@/components/theme-provider";
import { SidebarConfigProvider } from "@/contexts/sidebar-context";
import { Toaster } from "@/components/ui/sonner";

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <ThemeProvider defaultTheme="system" storageKey="nextjs-ui-theme">
      <SidebarConfigProvider>
        {children}
        <Toaster position="bottom-right" richColors closeButton />
      </SidebarConfigProvider>
    </ThemeProvider>
  );
}