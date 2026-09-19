"use client";
import { cn } from "@/lib/utils";
export default function StyleAwareWrapper({ children, lyraClassName, defaultClassName, className }: { children: React.ReactNode; lyraClassName?: string; defaultClassName?: string; className?: string }) {
  // SafeResale uses default style only — lyra variant kept for template parity
  return <div className={cn(defaultClassName, className)}>{children}</div>;
}
