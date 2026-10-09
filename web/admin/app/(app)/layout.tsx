"use client";

import React from "react";
import RequireAuth from "@/components/require-auth";
import { AppSidebar } from "@/components/app-sidebar";
import { SiteHeader } from "@/components/site-header";
import { SiteFooter } from "@/components/site-footer";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { useSidebarConfig } from "@/hooks/use-sidebar-config";

function DashboardShell({ children }: { children: React.ReactNode }) {
  const { config } = useSidebarConfig();

  return (
    <SidebarProvider
      style={
        {
          "--sidebar-width": "16rem",
          "--sidebar-width-icon": "3rem",
          "--header-height": "calc(var(--spacing) * 14)",
        } as React.CSSProperties
      }
      className={config.collapsible === "none" ? "sidebar-none-mode" : ""}
    >
      {config.side === "left" ? (
        <>
          <AppSidebar
            variant={config.variant}
            collapsible={config.collapsible}
            side={config.side}
          />
          <SidebarInset className="min-w-0 overflow-hidden">
            <SiteHeader />
            <div className="flex flex-1 flex-col min-w-0 overflow-hidden">
              <div className="@container/main flex flex-1 flex-col gap-2 min-w-0">
                <div className="flex flex-col gap-4 py-4 md:gap-6 md:py-6 min-w-0">
                  {children}
                </div>
              </div>
            </div>
            <SiteFooter />
          </SidebarInset>
        </>
      ) : (
        <>
          <SidebarInset className="min-w-0 overflow-hidden">
            <SiteHeader />
            <div className="flex flex-1 flex-col min-w-0 overflow-hidden">
              <div className="@container/main flex flex-1 flex-col gap-2 min-w-0">
                <div className="flex flex-col gap-4 py-4 md:gap-6 md:py-6 min-w-0">
                  {children}
                </div>
              </div>
            </div>
            <SiteFooter />
          </SidebarInset>
          <AppSidebar
            variant={config.variant}
            collapsible={config.collapsible}
            side={config.side}
          />
        </>
      )}
    </SidebarProvider>
  );
}

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <RequireAuth>
      <DashboardShell>{children}</DashboardShell>
    </RequireAuth>
  );
}
