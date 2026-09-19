"use client"

import { ChevronLeft, ChevronRight } from "lucide-react"
import { Button } from "@heroui/react"

export function Pager({
  page,
  pageSize,
  total,
  onPage,
}: {
  page: number
  pageSize: number
  total: number
  onPage: (p: number) => void
}) {
  const pages = Math.max(1, Math.ceil(total / pageSize))
  if (pages <= 1) return null
  const from = total === 0 ? 0 : (page - 1) * pageSize + 1
  const to = Math.min(total, page * pageSize)
  return (
    <div className="flex items-center justify-between gap-3 px-1 py-3">
      <p className="text-xs text-muted-foreground">
        {from}–{to} of {total}
      </p>
      <div className="flex items-center gap-1.5">
        <Button variant="outline" isIconOnly size="sm" className="h-8 w-8" isDisabled={page <= 1} onPress={() => onPage(page - 1)} aria-label="Previous page">
          <ChevronLeft className="size-4" />
        </Button>
        <span className="min-w-10 text-center text-xs text-muted-foreground tabular-nums">
          {page} / {pages}
        </span>
        <Button variant="outline" isIconOnly size="sm" className="h-8 w-8" isDisabled={page >= pages} onPress={() => onPage(page + 1)} aria-label="Next page">
          <ChevronRight className="size-4" />
        </Button>
      </div>
    </div>
  )
}
