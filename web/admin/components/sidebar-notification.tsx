"use client"

import * as React from "react"
import { X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"

export function SidebarNotification() {
  const [isVisible, setIsVisible] = React.useState(true)

  if (!isVisible) return null

  return (
    <Card className="mb-3 py-0 border bg-sidebar-accent/50">
      <CardContent className="p-4 relative">
        <Button
          variant="ghost"
          size="sm"
          className="absolute top-2 right-2 h-6 w-6 p-0 hover:bg-sidebar-accent"
          onClick={() => setIsVisible(false)}
        >
          <X className="h-3 w-3" />
          <span className="sr-only">Close notification</span>
        </Button>
        <div className="pr-6">
          <h3 className="font-semibold text-sidebar-foreground mb-1.5 mt-1 text-sm">
            Welcome to SafeResale
          </h3>
          <p className="text-xs text-muted-foreground leading-relaxed">
            Moderation queue, escrow &amp; trust operations — all in one place.
          </p>
        </div>
      </CardContent>
    </Card>
  )
}
