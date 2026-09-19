"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { useEffect, useState } from "react"
import { EllipsisVertical, LogOut, Settings, ShieldCheck, UserCircle } from "lucide-react"
import { clearSession, getSessionUser } from "@/lib/api"
import { Avatar, Button, Dropdown, Label, Separator } from "@heroui/react"
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
      <SidebarHeader className="border-b">
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" className="h-auto py-3 cursor-pointer">
              <Link href="/dashboard" className="gap-0 flex items-center">
                <img src="/logo.svg" alt="SafeResale" className="h-[52px] w-auto max-w-[190px] object-contain" />
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        {NAV_GROUPS.map((group) => (
          <SidebarGroup key={group.title}>
            <SidebarGroupLabel>{group.title}</SidebarGroupLabel>
            <SidebarMenu>
              {group.items.map((item) => {
                const Icon = item.icon
                const active = seg === item.segment
                return (
                  <SidebarMenuItem key={item.href}>
                    <SidebarMenuButton isActive={active} className="cursor-pointer">
                      <Link href={item.href} className="flex items-center gap-2 w-full">
                        <Icon />
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

      <SidebarFooter>
        <NavUser user={user} />
      </SidebarFooter>
    </Sidebar>
  )
}

function NavUser({ user }: { user: any }) {
  const { isMobile } = useSidebar()
  const name = user?.name || "Admin"
  const email = user?.email || "signed in"
  const initial = (name || email || "A")[0]?.toUpperCase() ?? "A"

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <Dropdown>
          <Dropdown.Trigger>
            <SidebarMenuButton size="lg" className="cursor-pointer data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground">
              <Avatar className="size-8 rounded-lg">
                <Avatar.Fallback className="rounded-lg bg-primary text-xs font-bold text-primary-foreground">
                  {initial}
                </Avatar.Fallback>
              </Avatar>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-medium">{name}</span>
                <span className="truncate text-xs text-muted-foreground">{email}</span>
              </div>
              <EllipsisVertical className="ml-auto size-4" />
            </SidebarMenuButton>
          </Dropdown.Trigger>
          <Dropdown.Popover>
            <Dropdown.Menu>
              <Dropdown.Item id="profile" textValue="Profile">
                <Link href="/profile" className="flex items-center gap-2">
                  <UserCircle className="size-4" /> Profile
                </Link>
              </Dropdown.Item>
              <Dropdown.Item id="settings" textValue="Settings">
                <Link href="/settings" className="flex items-center gap-2">
                  <Settings className="size-4" /> Settings
                </Link>
              </Dropdown.Item>
              <Dropdown.Item id="signout" textValue="Sign out" variant="danger" onPress={() => clearSession()}>
                <Label className="flex items-center gap-2">
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
