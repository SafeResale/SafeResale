"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { LogOut, ShieldCheck, UserCircle } from "lucide-react"
import { clearSession, getSessionUser } from "@/lib/api"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
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
    <header className="flex h-14 shrink-0 items-center gap-2 border-b border-border/50 transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-14 backdrop-blur-sm bg-background/80">
      <div className="flex w-full items-center gap-1 px-4 py-3 lg:gap-2 lg:px-6">
        <SidebarTrigger className="-ml-1" />

        <div className="mx-1.5 h-4 w-px bg-border/60" />

        <div className="flex min-w-0 flex-col">
          <span className="truncate text-sm font-semibold tracking-tight">{item?.label ?? "SafeResale Admin"}</span>
          <span className="hidden text-[11px] text-muted-foreground/70 sm:block">Verification Trust Engine</span>
        </div>

        <div className="ml-auto flex items-center gap-1.5">
          <div
            className="hidden items-center gap-1.5 rounded-full border border-border/60 bg-muted/40 px-2.5 py-1 text-xs sm:flex"
            title="Backend health"
          >
            <span className={cn("size-1.5 rounded-full", health === "ok" ? "bg-success" : health === "down" ? "bg-destructive" : "bg-warning")} />
            <span className="text-muted-foreground/80">{health === "ok" ? "API online" : health === "down" ? "API offline" : "…"}</span>
          </div>

          <ThemeToggle />

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button type="button" className="flex h-9 items-center gap-2 rounded-lg px-2 text-sm outline-none transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:ring-ring/50 focus-visible:ring-[3px]">
                <Avatar className="size-7">
                  <AvatarFallback className="bg-gradient-to-br from-accent to-accent/70 text-[10px] font-bold text-accent-foreground shadow-sm">
                    {((user?.name || user?.email || "A")[0] || "A").toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <span className="hidden max-w-[140px] truncate text-sm lg:block">{user?.name || user?.email}</span>
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="min-w-[12rem] rounded-xl">
              <DropdownMenuItem asChild>
                <Link href="/profile" className="flex items-center gap-2.5">
                  <UserCircle className="size-4 text-muted-foreground" /> Your profile
                </Link>
              </DropdownMenuItem>
              <DropdownMenuItem asChild>
                <Link href="/settings" className="flex items-center gap-2.5">
                  <ShieldCheck className="size-4 text-muted-foreground" /> Admin settings
                </Link>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onSelect={() => clearSession()}
                className="flex items-center gap-2.5 text-destructive focus:text-destructive"
              >
                <LogOut className="size-4" /> Sign out
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </header>
  )
}
