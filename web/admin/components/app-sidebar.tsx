"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { useEffect, useState } from "react"
import { EllipsisVertical, LogOut, Settings, ShieldCheck, UserCircle, ChevronsUpDown } from "lucide-react"
import { clearSession, getSessionUser } from "@/lib/api"
import { Avatar, Dropdown, Label } from "@heroui/react"
import { NAV_GROUPS } from "@/components/shell/nav"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarSeparator,
  useSidebar,
} from "@/components/ui/sidebar"

function activeSegment(pathname: string): string {
  const parts = pathname.split("/").filter(Boolean)
  if (parts[0] === "content" && parts[1]) return parts[1]
  return parts[0] || "dashboard"
}

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  const pathname = usePathname()
  const seg = activeSegment(pathname)
  const [user, setUser] = useState<any>(null)

  useEffect(() => {
    setUser(getSessionUser())
  }, [])

  return (
    <Sidebar {...props}>
      <SidebarHeader>
        <Link href="/dashboard" className="flex items-center gap-0 px-1 pt-1 pb-0.5">
          <img src="/logo.svg" alt="SafeResale" className="h-[44px] w-auto max-w-[180px] object-contain" />
        </Link>
      </SidebarHeader>

      <SidebarSeparator />

      <SidebarContent>
        {NAV_GROUPS.map((group, gi) => (
          <SidebarGroup key={group.title}>
            {gi > 0 && <SidebarSeparator className="my-1" />}
            <SidebarGroupLabel>{group.title}</SidebarGroupLabel>
            <SidebarMenu>
              {group.items.map((item) => {
                const Icon = item.icon
                const active = seg === item.segment
                return (
                  <SidebarMenuItem key={item.href}>
                    <SidebarMenuButton isActive={active} className="cursor-pointer">
                      <Link href={item.href} className="flex items-center gap-2.5 w-full">
                        <Icon className="size-4 shrink-0" />
                        <span>{item.label}</span>
                      </Link>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                )
              })}
            </SidebarMenu>
          </SidebarGroup>
        ))}
      </SidebarContent>

      <SidebarSeparator />

      <SidebarFooter>
        <NavUser user={user} />
      </SidebarFooter>
    </Sidebar>
  )
}

function NavUser({ user }: { user: any }) {
  const name = user?.name || "Admin"
  const email = user?.email || "signed in"
  const initial = (name || email || "A")[0]?.toUpperCase() ?? "A"

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <Dropdown>
          <Dropdown.Trigger>
            <SidebarMenuButton size="lg" className="cursor-pointer data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground gap-3 py-2.5">
              <Avatar className="size-8 rounded-lg shrink-0">
                <Avatar.Fallback className="rounded-lg bg-gradient-to-br from-accent to-accent/70 text-[11px] font-bold text-accent-foreground shadow-sm">
                  {initial}
                </Avatar.Fallback>
              </Avatar>
              <div className="grid flex-1 text-left text-sm leading-tight min-w-0">
                <span className="truncate font-medium text-sidebar-foreground">{name}</span>
                <span className="truncate text-[11px] text-sidebar-foreground/50">{email}</span>
              </div>
              <ChevronsUpDown className="ml-auto size-3.5 text-sidebar-foreground/40 shrink-0" />
            </SidebarMenuButton>
          </Dropdown.Trigger>
          <Dropdown.Popover>
            <Dropdown.Menu>
              <Dropdown.Item id="profile" textValue="Profile">
                <Link href="/profile" className="flex items-center gap-2.5">
                  <UserCircle className="size-4 text-sidebar-foreground/60" /> Profile
                </Link>
              </Dropdown.Item>
              <Dropdown.Item id="settings" textValue="Settings">
                <Link href="/settings" className="flex items-center gap-2.5">
                  <Settings className="size-4 text-sidebar-foreground/60" /> Settings
                </Link>
              </Dropdown.Item>
              <Dropdown.Item id="signout" textValue="Sign out" variant="danger" onPress={() => clearSession()}>
                <Label className="flex items-center gap-2.5">
                  <LogOut className="size-4" /> Sign out
                </Label>
              </Dropdown.Item>
            </Dropdown.Menu>
          </Dropdown.Popover>
        </Dropdown>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
