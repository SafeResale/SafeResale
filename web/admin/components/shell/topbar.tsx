"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { LogOut, ShieldCheck, UserCircle } from "lucide-react"
import { clearSession, getSessionUser } from "@/lib/api"
import { Avatar, Button, Dropdown, Label } from "@heroui/react"
import { SidebarTrigger } from "@/components/ui/sidebar"
import { ThemeToggle } from "@/components/theme-toggle"
import { findItem } from "./nav"

function cn(...classes: (string | undefined | false)[]) {
  return classes.filter(Boolean).join(" ")
}

export function Topbar() {
  const pathname = usePathname()
  const [user, setUser] = useState<any>(null)
  const [health, setHealth] = useState<"ok" | "down" | "pending">("pending")

  useEffect(() => {
    setUser(getSessionUser())
    let live = true
    fetch("/health")
      .then((r) => r.json())
      .then((h) => live && setHealth(h?.status === "ok" ? "ok" : "down"))
      .catch(() => live && setHealth("down"))
    return () => { live = false }
  }, [])

  const parts = pathname.split("/").filter(Boolean)
  const seg = parts[0] === "content" && parts[1] ? parts[1] : parts[0] || "dashboard"
  const item = findItem(seg)

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-14">
      <div className="flex w-full items-center gap-1 px-4 py-3 lg:gap-2 lg:px-6">
        <SidebarTrigger className="-ml-1" />

        <div className="mx-2 h-4 w-px bg-border" />

        <div className="flex min-w-0 flex-col">
          <span className="truncate text-sm font-semibold">{item?.label ?? "SafeResale Admin"}</span>
          <span className="hidden text-xs text-muted-foreground sm:block">Verification Trust Engine</span>
        </div>

        <div className="ml-auto flex items-center gap-2">
          <div className="hidden items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs sm:flex" title="Backend health">
            <span className={cn("size-2 rounded-full", health === "ok" ? "bg-success" : health === "down" ? "bg-danger" : "bg-warning")} />
            <span className="text-muted-foreground">{health === "ok" ? "API online" : health === "down" ? "API offline" : "…"}</span>
          </div>

          <ThemeToggle />

          <Dropdown>
            <Dropdown.Trigger>
              <Button variant="ghost" className="h-9 gap-2 px-2">
                <Avatar className="size-7">
                  <Avatar.Fallback className="bg-primary text-[10px] font-bold text-primary-foreground">
                    {((user?.name || user?.email || "A")[0] || "A").toUpperCase()}
                  </Avatar.Fallback>
                </Avatar>
                <span className="hidden max-w-[140px] truncate text-sm lg:block">{user?.name || user?.email}</span>
              </Button>
            </Dropdown.Trigger>
            <Dropdown.Popover>
              <Dropdown.Menu>
                <Dropdown.Item id="profile" textValue="Your profile">
                  <Link href="/profile" className="flex items-center gap-2">
                    <UserCircle className="size-4" /> Your profile
                  </Link>
                </Dropdown.Item>
                <Dropdown.Item id="settings" textValue="Admin settings">
                  <Link href="/settings" className="flex items-center gap-2">
                    <ShieldCheck className="size-4" /> Admin settings
                  </Link>
                </Dropdown.Item>
                <Dropdown.Item id="signout" textValue="Sign out" variant="danger" onPress={() => clearSession()}>
                  <Label className="flex items-center gap-2 text-danger">
                    <LogOut className="size-4" /> Sign out
                  </Label>
                </Dropdown.Item>
              </Dropdown.Menu>
            </Dropdown.Popover>
          </Dropdown>
        </div>
      </div>
    </header>
  )
}
