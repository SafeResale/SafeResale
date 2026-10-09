"use client"
import { useEffect, useState, type ReactNode } from "react"
import { usePathname, useRouter } from "next/navigation"
import { Loader2 } from "lucide-react"
import { startAuthWatch } from "@/lib/api"

const PUBLIC_PATHS = ["/login"]

export default function RequireAuth({ children }: { children: ReactNode }) {
  const pathname = usePathname()
  const router = useRouter()
  const [ready, setReady] = useState(false)
  const pub = PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(p + "/"))

  useEffect(() => {
    startAuthWatch()
    const tok = localStorage.getItem("access_token")
    if (pub) { setReady(true); return }
    if (!tok) { router.replace("/login"); return }
    setReady(true)
  }, [pathname, pub, router])

  if (!ready)
    return (
      <div className="flex min-h-svh flex-col items-center justify-center gap-3 text-muted-foreground">
        <Loader2 className="size-6 animate-spin" />
        <p className="text-sm">Checking session…</p>
      </div>
    )
  return <>{children}</>
}
