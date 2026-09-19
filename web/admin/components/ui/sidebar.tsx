"use client"

import * as React from "react"
import { cn } from "@/lib/utils"

const SIDEBAR_WIDTH = "16rem"
const SIDEBAR_WIDTH_ICON = "3rem"
const SIDEBAR_COOKIE_NAME = "sidebar_state"
const SIDEBAR_COOKIE_MAX_AGE = 60 * 60 * 24 * 7

type SidebarContextProps = {
  state: "expanded" | "collapsed"
  open: boolean
  setOpen: (open: boolean) => void
  openMobile: boolean
  setOpenMobile: (open: boolean) => void
  isMobile: boolean
  toggleSidebar: () => void
}

const SidebarContext = React.createContext<SidebarContextProps | null>(null)

export function useSidebar() {
  const ctx = React.useContext(SidebarContext)
  if (!ctx) throw new Error("useSidebar must be used within SidebarProvider")
  return ctx
}

export function SidebarProvider({
  defaultOpen = true,
  open: openProp,
  onOpenChange: setOpenProp,
  className,
  style,
  children,
  ...props
}: React.ComponentProps<"div"> & {
  defaultOpen?: boolean
  open?: boolean
  onOpenChange?: (open: boolean) => void
}) {
  const isMobile = useIsMobile()
  const [openMobile, setOpenMobile] = React.useState(false)
  const [_open, _setOpen] = React.useState(defaultOpen)
  const open = openProp ?? _open

  const setOpen = React.useCallback(
    (value: boolean | ((value: boolean) => boolean)) => {
      const openState = typeof value === "function" ? value(open) : value
      setOpenProp?.(openState)
      _setOpen(openState)
      document.cookie = `${SIDEBAR_COOKIE_NAME}=${openState}; path=/; max-age=${SIDEBAR_COOKIE_MAX_AGE}`
    },
    [setOpenProp, open]
  )

  const toggleSidebar = React.useCallback(() => {
    isMobile ? setOpenMobile((o) => !o) : setOpen((o) => !o)
  }, [isMobile, setOpen])

  React.useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "b" && (e.metaKey || e.ctrlKey)) {
        e.preventDefault()
        toggleSidebar()
      }
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [toggleSidebar])

  const state = open ? "expanded" : "collapsed"
  const value = React.useMemo<SidebarContextProps>(
    () => ({ state, open, setOpen, isMobile, openMobile, setOpenMobile, toggleSidebar }),
    [state, open, setOpen, isMobile, openMobile, setOpenMobile, toggleSidebar]
  )

  return (
    <SidebarContext.Provider value={value}>
      <div
        style={{ "--sidebar-width": SIDEBAR_WIDTH, "--sidebar-width-icon": SIDEBAR_WIDTH_ICON, ...style } as React.CSSProperties}
        className={cn("group/sidebar-wrapper flex min-h-svh w-full", className)}
        {...props}
      >
        {children}
      </div>
    </SidebarContext.Provider>
  )
}

export function Sidebar({
  side = "left",
  variant = "sidebar",
  collapsible = "offcanvas",
  className,
  children,
  ...props
}: React.ComponentProps<"div"> & {
  side?: "left" | "right"
  variant?: "sidebar" | "floating" | "inset"
  collapsible?: "offcanvas" | "icon" | "none"
}) {
  const { isMobile, state, openMobile, setOpenMobile } = useSidebar()

  if (isMobile) {
    return (
      <div className={cn("fixed inset-y-0 left-0 z-50 w-[var(--sidebar-width)] bg-sidebar text-sidebar-foreground shadow-xl transition-transform duration-200", openMobile ? "translate-x-0" : "-translate-x-full", className)} {...props}>
        <div className="absolute top-3 right-3 z-10">
          <button onClick={() => setOpenMobile(false)} className="rounded-md p-1.5 hover:bg-sidebar-accent">✕</button>
        </div>
        {children}
      </div>
    )
  }

  if (collapsible === "none") {
    return (
      <div className={cn("flex h-full w-[var(--sidebar-width)] flex-col bg-sidebar text-sidebar-foreground", className)} {...props}>
        {children}
      </div>
    )
  }

  return (
    <div className="group peer hidden md:block text-sidebar-foreground" data-state={state} data-collapsible={state === "collapsed" ? collapsible : ""} data-variant={variant} data-side={side}>
      <div className={cn("relative w-[var(--sidebar-width)] bg-transparent transition-[width] duration-200 ease-linear group-data-[collapsible=offcanvas]:w-0 group-data-[side=right]:rotate-180", variant === "floating" || variant === "inset" ? "group-data-[collapsible=icon]:w-[calc(var(--sidebar-width-icon)+1rem)]" : "group-data-[collapsible=icon]:w-[var(--sidebar-width-icon)]")} />
      <div className={cn("fixed inset-y-0 z-10 hidden h-svh w-[var(--sidebar-width)] transition-[left,right,width] duration-200 ease-linear md:flex", variant === "floating" || variant === "inset" ? "p-2 group-data-[collapsible=icon]:w-[calc(var(--sidebar-width-icon)+1rem+2px)]" : "group-data-[collapsible=icon]:w-[var(--sidebar-width-icon)] group-data-[side=left]:border-r group-data-[side=right]:border-l", side === "left" ? "left-0 data-[collapsible=offcanvas]:left-[calc(var(--sidebar-width)*-1)]" : "right-0 data-[collapsible=offcanvas]:right-[calc(var(--sidebar-width)*-1)]", className)} {...props}>
        <div className="flex size-full flex-col bg-sidebar group-data-[variant=floating]:rounded-lg group-data-[variant=floating]:shadow-sm group-data-[variant=floating]:ring-1 group-data-[variant=floating]:ring-sidebar-border">
          {children}
        </div>
      </div>
    </div>
  )
}

export function SidebarInset({ className, ...props }: React.ComponentProps<"main">) {
  return <main className={cn("relative flex w-full flex-1 flex-col bg-background md:peer-data-[variant=inset]:m-2 md:peer-data-[variant=inset]:ml-0 md:peer-data-[variant=inset]:rounded-xl md:peer-data-[variant=inset]:shadow-sm md:peer-data-[variant=inset]:peer-data-[state=collapsed]:ml-2", className)} {...props} />
}

export function SidebarTrigger({ className, onClick, ...props }: React.ComponentProps<"button">) {
  const { toggleSidebar } = useSidebar()
  return (
    <button data-sidebar="trigger" className={cn("inline-flex items-center justify-center rounded-md p-2 text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground transition-colors", className)} onClick={(e) => { onClick?.(e); toggleSidebar() }} {...props}>
      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect width="18" height="18" x="3" y="3" rx="2" /><path d="M9 3v18" /></svg>
      <span className="sr-only">Toggle Sidebar</span>
    </button>
  )
}

export function SidebarHeader({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("flex flex-col gap-2 p-2", className)} {...props} />
}

export function SidebarFooter({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("flex flex-col gap-2 p-2", className)} {...props} />
}

export function SidebarContent({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("no-scrollbar flex min-h-0 flex-1 flex-col overflow-auto group-data-[collapsible=icon]:overflow-hidden", className)} {...props} />
}

export function SidebarGroup({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("relative flex w-full min-w-0 flex-col p-2", className)} {...props} />
}

export function SidebarGroupLabel({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("flex h-8 shrink-0 items-center rounded-md px-2 text-xs font-medium text-sidebar-foreground/70 outline-hidden transition-[margin,opacity] duration-200 ease-linear group-data-[collapsible=icon]:-mt-8 group-data-[collapsible=icon]:opacity-0 [&>svg]:size-4 [&>svg]:shrink-0", className)} {...props} />
}

export function SidebarGroupContent({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("w-full text-sm", className)} {...props} />
}

export function SidebarMenu({ className, ...props }: React.ComponentProps<"ul">) {
  return <ul className={cn("flex w-full min-w-0 flex-col gap-1", className)} {...props} />
}

export function SidebarMenuItem({ className, ...props }: React.ComponentProps<"li">) {
  return <li className={cn("group/menu-item relative", className)} {...props} />
}

export function SidebarMenuButton({
  isActive = false,
  variant = "default",
  size = "default",
  tooltip,
  className,
  children,
  ...props
}: React.ComponentProps<"button"> & {
  isActive?: boolean
  tooltip?: string
  variant?: "default" | "outline"
  size?: "default" | "sm" | "lg"
}) {
  const classes = cn(
    "peer/menu-button flex w-full items-center gap-2 overflow-hidden rounded-md p-2 text-left text-sm outline-hidden transition-[width,height,padding] hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus-visible:ring-2 active:bg-sidebar-accent active:text-sidebar-accent-foreground disabled:pointer-events-none disabled:opacity-50 group-has-data-[sidebar=menu-action]/menu-item:pr-8 data-open:hover:bg-sidebar-accent data-open:hover:text-sidebar-accent-foreground data-active:bg-sidebar-accent data-active:font-medium data-active:text-sidebar-accent-foreground [&_svg]:size-4 [&_svg]:shrink-0 [&>span:last-child]:truncate",
    size === "sm" && "h-7 text-xs",
    size === "lg" && "h-12 text-sm group-data-[collapsible=icon]:p-0!",
    (size === "default" || !size) && "h-8 text-sm",
    variant === "outline" && "bg-background shadow-[0_0_0_1px_var(--sidebar-border)] hover:bg-sidebar-accent hover:text-sidebar-accent-foreground hover:shadow-[0_0_0_1px_var(--sidebar-accent)]",
    isActive && "bg-sidebar-accent font-medium text-sidebar-accent-foreground",
    "group-data-[collapsible=icon]:size-8! group-data-[collapsible=icon]:p-2!",
    className
  )

  return (
    <button className={classes} data-active={isActive} {...props}>
      {children}
    </button>
  )
}

function SidebarSeparator({ className, ...props }: React.ComponentProps<"hr">) {
  return <hr className={cn("mx-2 w-auto bg-sidebar-border", className)} {...props} />
}

export { SidebarSeparator }

function useIsMobile() {
  const [isMobile, setIsMobile] = React.useState<boolean | undefined>(undefined)
  React.useEffect(() => {
    const mql = window.matchMedia("(max-width: 767px)")
    const onChange = () => setIsMobile(window.innerWidth < 768)
    mql.addEventListener("change", onChange)
    setIsMobile(window.innerWidth < 768)
    return () => mql.removeEventListener("change", onChange)
  }, [])
  return !!isMobile
}
