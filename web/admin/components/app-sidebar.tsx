"use client"

import * as React from "react"
import {
  LayoutDashboard,
  ShieldAlert,
  ScrollText,
  Package,
  FolderTree,
  Wallet,
  Users,
  Flag,
  Mail,
  FileText,
  HelpCircle,
  Lightbulb,
  Cpu,
  Bell,
  Settings,
  UserCircle,
} from "lucide-react"
import Link from "next/link"
import { useEffect, useState } from "react"
import { getSessionUser } from "@/lib/api"
import { SidebarNotification } from "@/components/sidebar-notification"
import { NavMain } from "@/components/nav-main"
import { NavUser } from "@/components/nav-user"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"

const navGroups = [
  {
    label: "Overview",
    items: [
      { title: "Dashboard", url: "/dashboard", icon: LayoutDashboard },
      { title: "Queue", url: "/queue", icon: ShieldAlert },
      { title: "Audit trail", url: "/audit", icon: ScrollText },
    ],
  },
  {
    label: "Marketplace",
    items: [
      { title: "Listings", url: "/listings", icon: Package },
      { title: "Categories", url: "/categories", icon: FolderTree },
      { title: "Escrow", url: "/escrow", icon: Wallet },
    ],
  },
  {
    label: "People & Support",
    items: [
      { title: "Users", url: "/users", icon: Users },
      { title: "Reports", url: "/reports", icon: Flag },
      { title: "Messages", url: "/messages", icon: Mail },
    ],
  },
  {
    label: "Content",
    items: [
      { title: "Blogs", url: "/content/blogs", icon: FileText },
      { title: "FAQs", url: "/content/faqs", icon: HelpCircle },
      { title: "Tips", url: "/content/tips", icon: Lightbulb },
    ],
  },
  {
    label: "System",
    items: [
      { title: "Models", url: "/models", icon: Cpu },
      { title: "Notifications", url: "/notifications", icon: Bell },
      { title: "Settings", url: "/settings", icon: Settings },
      { title: "Profile", url: "/profile", icon: UserCircle },
    ],
  },
]

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  const [user, setUser] = useState<{ name: string; email: string; avatar: string }>({
    name: "Admin",
    email: "signed in",
    avatar: "",
  })

  useEffect(() => {
    const u: any = getSessionUser()
    if (u) {
      setUser({
        name: u.name || u.displayName || "Admin",
        email: u.email || "signed in",
        avatar: u.avatar || "",
      })
    }
  }, [])

  return (
    <Sidebar {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild>
              <Link href="/dashboard">
                <div className="flex aspect-square size-8 items-center justify-center overflow-hidden rounded-lg border bg-card shadow-sm">
                  <img src="/app_logo.png" alt="SafeResale" className="size-8 object-contain" />
                </div>
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-medium">SafeResale</span>
                  <span className="truncate text-xs">Admin Dashboard</span>
                </div>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        {navGroups.map((group) => (
          <NavMain key={group.label} label={group.label} items={group.items} />
        ))}
      </SidebarContent>
      <SidebarFooter>
        <SidebarNotification />
        <NavUser user={user} />
      </SidebarFooter>
    </Sidebar>
  )
}
