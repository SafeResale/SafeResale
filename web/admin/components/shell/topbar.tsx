"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { LogOut, ShieldCheck, UserCircle } from "lucide-react";
import { clearSession, getSessionUser } from "@/lib/api";
import { cn } from "@/lib/utils";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { Separator } from "@/components/ui/separator";
import { ThemeToggle } from "@/components/theme-toggle";
import { findItem } from "./nav";

export function Topbar() {
  const pathname = usePathname();
  const [user, setUser] = useState<any>(null);
  const [health, setHealth] = useState<"ok" | "down" | "pending">("pending");

  useEffect(() => {
    setUser(getSessionUser());
    let live = true;
    fetch("/health")
      .then((r) => r.json())
      .then((h) => live && setHealth(h?.status === "ok" ? "ok" : "down"))
      .catch(() => live && setHealth("down"));
    return () => {
      live = false;
    };
  }, []);

  const parts = pathname.split("/").filter(Boolean);
  const seg = parts[0] === "content" && parts[1] ? parts[1] : parts[0] || "dashboard";
  const item = findItem(seg);

  return (
    <header className="flex h-(--header-height) shrink-0 items-center gap-2 border-b transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-(--header-height)">
      <div className="flex w-full items-center gap-1 px-4 py-3 lg:gap-2 lg:px-6">
        <SidebarTrigger className="-ml-1" />

        <Separator orientation="vertical" className="mx-2 data-[orientation=vertical]:h-4" />

        <div className="flex min-w-0 flex-col">
          <span className="truncate text-sm font-semibold">{item?.label ?? "SafeResale Admin"}</span>
          <span className="hidden text-xs text-muted-foreground sm:block">Verification Trust Engine</span>
        </div>

        <div className="ml-auto flex items-center gap-2">
          <div
            className="hidden items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs sm:flex"
            title="Backend health"
          >
            <span className={cn("size-2 rounded-full", health === "ok" ? "bg-success" : health === "down" ? "bg-danger" : "bg-warning")} />
            <span className="text-muted-foreground">{health === "ok" ? "API online" : health === "down" ? "API offline" : "…"}</span>
          </div>

          <ThemeToggle />

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" className="h-9 gap-2 px-2">
                <Avatar className="size-7">
                  <AvatarImage src="" alt="" />
                  <AvatarFallback className="bg-primary text-[10px] font-bold text-primary-foreground">
                    {((user?.name || user?.email || "A")[0] || "A").toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <span className="hidden max-w-[140px] truncate text-sm lg:block">{user?.name || user?.email}</span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-56">
              <DropdownMenuLabel className="space-y-0.5">
                <p className="text-sm font-medium">{user?.name || "Admin"}</p>
                {user?.email && <p className="text-xs font-normal text-muted-foreground">{user.email}</p>}
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem asChild>
                <Link href="/profile">
                  <UserCircle className="size-4" /> Your profile
                </Link>
              </DropdownMenuItem>
              <DropdownMenuItem asChild>
                <Link href="/settings" className="flex items-center gap-2">
                  <ShieldCheck className="size-4" /> Admin settings
                </Link>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                className="text-destructive focus:text-destructive"
                onClick={() => {
                  clearSession();
                }}
              >
                <LogOut className="size-4" /> Sign out
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </header>
  );
}