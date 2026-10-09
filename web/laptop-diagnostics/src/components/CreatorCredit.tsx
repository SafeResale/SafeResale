import { cn } from '@/lib/utils';

export function CreatorCredit({ className }: { className?: string }) {
  return (
    <span className={cn('text-[10px] text-muted-foreground/60', className)}>
      Created by Yuan Mariano
    </span>
  );
}