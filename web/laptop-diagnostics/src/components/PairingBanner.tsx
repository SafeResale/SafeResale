import React from 'react';
import { usePairing } from '@/context/PairingContext';
import { ShieldCheck, AlertCircle, RefreshCw, Laptop } from 'lucide-react';

export function PairingBanner() {
  const { token, listingId, isPaired, isPairing, pairingError } = usePairing();

  if (!token && !listingId) return null;

  return (
    <div className="w-full bg-secondary/80 border-b border-border py-2 px-4 transition-all">
      <div className="max-w-6xl mx-auto flex flex-wrap items-center justify-between gap-2 text-xs">
        <div className="flex items-center gap-2">
          <Laptop className="w-3.5 h-3.5 text-primary" />
          <span className="font-medium text-foreground">SafeResale Session:</span>
          {isPairing && (
            <span className="inline-flex items-center gap-1.5 text-muted-foreground font-mono">
              <RefreshCw className="w-3 h-3 animate-spin text-primary" /> Connecting...
            </span>
          )}
          {isPaired && (
            <span className="inline-flex items-center gap-1.5 text-emerald-500 font-semibold font-mono">
              <ShieldCheck className="w-3.5 h-3.5" /> Connected to Listing
              {listingId ? ` #${listingId.slice(-6)}` : ''}
            </span>
          )}
          {pairingError && (
            <span className="inline-flex items-center gap-1.5 text-destructive font-mono">
              <AlertCircle className="w-3.5 h-3.5" /> {pairingError}
            </span>
          )}
        </div>
        {token && (
          <span className="font-mono text-[11px] text-muted-foreground bg-background/60 px-2 py-0.5 rounded border border-border/50">
            Token: <span className="font-semibold text-foreground">{token}</span>
          </span>
        )}
      </div>
    </div>
  );
}
