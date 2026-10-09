"use client"

import * as React from "react"
import { useRouter } from "next/navigation"
import {
  Search,
  LayoutDashboard,
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
  ScrollText,
  ShieldAlert,
  type LucideIcon,
} from "lucide-react"

import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { cn } from "@/lib/utils"

interface SearchItem {
  title: string
  url: string
  group: string
  icon?: LucideIcon
}

interface CommandSearchProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function CommandSearch({ open, onOpenChange }: CommandSearchProps) {
  const router = useRouter()
  const [query, setQuery] = React.useState("")

  const searchItems: SearchItem[] = [
    { title: "Dashboard", url: "/dashboard", group: "Overview", icon: LayoutDashboard },
    { title: "Queue", url: "/queue", group: "Overview", icon: ShieldAlert },
    { title: "Audit trail", url: "/audit", group: "Overview", icon: ScrollText },
    { title: "Listings", url: "/listings", group: "Marketplace", icon: Package },
    { title: "Categories", url: "/categories", group: "Marketplace", icon: FolderTree },
    { title: "Escrow", url: "/escrow", group: "Marketplace", icon: Wallet },
    { title: "Users", url: "/users", group: "People & Support", icon: Users },
    { title: "Reports", url: "/reports", group: "People & Support", icon: Flag },
    { title: "Messages", url: "/messages", group: "People & Support", icon: Mail },
    { title: "Blogs", url: "/content/blogs", group: "Content", icon: FileText },
    { title: "FAQs", url: "/content/faqs", group: "Content", icon: HelpCircle },
    { title: "Tips", url: "/content/tips", group: "Content", icon: Lightbulb },
    { title: "Models", url: "/models", group: "System", icon: Cpu },
    { title: "Notifications", url: "/notifications", group: "System", icon: Bell },
    { title: "Settings", url: "/settings", group: "System", icon: Settings },
    { title: "Profile", url: "/profile", group: "System", icon: UserCircle },
  ]

  const filtered = query.trim() === ""
    ? searchItems
    : searchItems.filter((i) => i.title.toLowerCase().includes(query.toLowerCase()) || i.group.toLowerCase().includes(query.toLowerCase()))

  const grouped = filtered.reduce((acc, item) => {
    if (!acc[item.group]) acc[item.group] = []
    acc[item.group].push(item)
    return acc
  }, {} as Record<string, SearchItem[]>)

  const handleSelect = (url: string) => {
    router.push(url)
    onOpenChange(false)
    setQuery("")
  }

  React.useEffect(() => {
    if (!open) setQuery("")
  }, [open])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="overflow-hidden p-0 shadow-2xl border max-w-[640px] gap-0 [&>button:last-child]:hidden">
        <DialogTitle className="sr-only">Command Search</DialogTitle>
        <div className="flex items-center border-b px-3">
          <Search className="mr-2 h-4 w-4 shrink-0 opacity-50" />
          <Input
            placeholder="Search pages..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="flex h-12 w-full rounded-none border-0 bg-transparent px-0 py-3 text-[15px] shadow-none focus-visible:ring-0 focus-visible:ring-offset-0 placeholder:text-muted-foreground"
            autoFocus
          />
        </div>
        <ScrollArea className="max-h-[400px] overflow-y-auto">
          <div className="p-2">
            {Object.keys(grouped).length === 0 ? (
              <div className="flex h-12 items-center justify-center text-sm text-muted-foreground">No results found.</div>
            ) : (
              Object.entries(grouped).map(([group, items]) => (
                <div key={group} className="[&:not(:first-child)]:mt-4">
                  <div className="px-2 py-1.5 text-xs font-medium text-muted-foreground">{group}</div>
                  <div className="space-y-1">
                    {items.map((item) => {
                      const Icon = item.icon
                      return (
                        <button
                          key={item.url}
                          onClick={() => handleSelect(item.url)}
                          className={cn(
                            "relative flex h-9 w-full cursor-pointer select-none items-center gap-2 rounded-md px-3 text-sm text-foreground outline-none transition-colors hover:bg-accent hover:text-accent-foreground",
                          )}
                        >
                          {Icon && <Icon className="h-4 w-4 shrink-0 text-muted-foreground" />}
                          <span>{item.title}</span>
                        </button>
                      )
                    })}
                  </div>
                </div>
              ))
            )}
          </div>
        </ScrollArea>
      </DialogContent>
    </Dialog>
  )
}

export function SearchTrigger({ onClick }: { onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className="inline-flex items-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50 border border-input bg-background shadow-sm hover:bg-accent hover:text-accent-foreground h-8 px-3 py-1 relative w-full justify-start text-muted-foreground sm:pr-12 md:w-36 lg:w-56"
    >
      <Search className="mr-2 h-3.5 w-3.5" />
      <span className="hidden lg:inline-flex">Search...</span>
      <span className="inline-flex lg:hidden">Search...</span>
      <kbd className="pointer-events-none absolute right-1.5 top-1.5 hidden h-4 select-none items-center gap-1 rounded border bg-muted px-1.5 font-mono text-[10px] font-medium opacity-100 sm:flex">
        <span className="text-xs">⌘</span>K
      </kbd>
    </button>
  )
}
