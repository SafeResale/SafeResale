"use client"

import { AppSidebar } from "@/components/app-sidebar"
import { SiteHeader } from "@/components/site-header"
import { SiteFooter } from "@/components/site-footer"
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar"
import { useSidebarConfig } from "@/hooks/use-sidebar-config"

export function AppShell({ children }: { children: React.ReactNode }) {
  const { config } = useSidebarConfig()
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
      <AppSidebar variant={config.variant} collapsible={config.collapsible} side={config.side} />
      <SidebarInset>
        <SiteHeader />
        <div className="flex flex-1 flex-col">
          <div className="@container/main flex flex-1 flex-col gap-2">
            <div className="flex flex-col gap-4 px-4 py-4 md:gap-6 md:py-6 lg:px-6">{children}</div>
          </div>
        </div>
        <SiteFooter />
      </SidebarInset>
    </SidebarProvider>
  )
}
