"use client";
import { useEffect, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Spinner } from "@/components/ui/spinner";
import { startAuthWatch } from "@/lib/api";

const PUBLIC_PATHS = ["/login"];

export default function RequireAuth({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [ready, setReady] = useState(false);
  const pub = PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(p + "/"));

  useEffect(() => {
    // Start the proactive refresh timer (JWT expiry-based)
    startAuthWatch();

    const tok = localStorage.getItem("access_token");
    if (pub) {
      setReady(true);
      return;
    }
    if (!tok) {
      router.replace("/login");
      return;
    }
    setReady(true);
  }, [pathname, pub, router]);

  if (!ready)
    return (
      <div className="flex min-h-svh flex-col items-center justify-center gap-3 text-muted-foreground">
        <Spinner className="size-6" />
        <p className="text-sm">Checking session…</p>
      </div>
    );
  return <>{children}</>;
}