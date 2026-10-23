import React, { createContext, useContext, useEffect, useState, useCallback, ReactNode } from 'react';

export function getApiBase(): string {
  if (typeof window === 'undefined') return 'http://localhost:8000';
  if ((import.meta as any).env?.VITE_API_BASE) {
    return ((import.meta as any).env.VITE_API_BASE as string).replace(/\/$/, '');
  }
  if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
    return 'http://localhost:8000';
  }
  return `${window.location.origin}/api`;
}

interface PairingContextType {
  token: string | null;
  sessionId: string | null;
  listingId: string | null;
  isPaired: boolean;
  isPairing: boolean;
  pairingError: string | null;
  reportProgress: () => Promise<void>;
  submitResults: (tests: any[]) => Promise<{ score: number; received: boolean }>;
}

const PairingContext = createContext<PairingContextType | undefined>(undefined);

export function PairingProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => {
    if (typeof window === 'undefined') return null;
    const urlParams = new URLSearchParams(window.location.search);
    const qToken = urlParams.get('token');
    if (qToken) {
      localStorage.setItem('saferesale_pairing_token', qToken);
      return qToken;
    }
    return localStorage.getItem('saferesale_pairing_token') || null;
  });

  const [sessionId, setSessionId] = useState<string | null>(() => {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem('saferesale_session_id') || null;
  });

  const [listingId, setListingId] = useState<string | null>(() => {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem('saferesale_listing_id') || null;
  });

  const [isPaired, setIsPaired] = useState<boolean>(false);
  const [isPairing, setIsPairing] = useState<boolean>(false);
  const [pairingError, setPairingError] = useState<string | null>(null);

  // Automatically attempt pairing whenever token is available
  useEffect(() => {
    if (!token) return;

    let isMounted = true;
    const pairDevice = async () => {
      setIsPairing(true);
      setPairingError(null);
      const apiBase = getApiBase();

      const deviceInfo = {
        model: navigator.userAgent.slice(0, 80),
        platform: navigator.platform,
        screen: `${window.screen.width}x${window.screen.height}`,
        cores: navigator.hardwareConcurrency || 4,
        device_type: 'laptop',
      };

      try {
        const pairRes = await fetch(`${apiBase}/diagnosis/pair`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            token,
            device: deviceInfo,
            device_id: 'laptop_web_' + (navigator.hardwareConcurrency || 'x') + '_' + window.screen.width,
          }),
        });

        if (pairRes.ok) {
          const data = await pairRes.json();
          if (!isMounted) return;
          const sid = data.session?._id;
          const lid = data.listing_id || data.session?.listing_id;
          if (sid) {
            setSessionId(sid);
            localStorage.setItem('saferesale_session_id', sid);
          }
          if (lid) {
            setListingId(lid);
            localStorage.setItem('saferesale_listing_id', lid);
          }
          setIsPaired(true);
          return;
        }

        // If token already paired, check existing session by token
        const lookupRes = await fetch(`${apiBase}/diagnosis/session-by-token?token=${encodeURIComponent(token)}`);
        if (lookupRes.ok) {
          const data = await lookupRes.json();
          if (!isMounted) return;
          const sid = data.session?._id;
          const lid = data.listing_id || data.session?.listing_id;
          if (sid) {
            setSessionId(sid);
            localStorage.setItem('saferesale_session_id', sid);
          }
          if (lid) {
            setListingId(lid);
            localStorage.setItem('saferesale_listing_id', lid);
          }
          setIsPaired(true);
        } else {
          const errData = await lookupRes.json().catch(() => null);
          if (isMounted) {
            setPairingError(errData?.detail?.message || 'Invalid or expired pairing token.');
          }
        }
      } catch (err: any) {
        if (isMounted) {
          setPairingError(err?.message || 'Could not connect to SafeResale server.');
        }
      } finally {
        if (isMounted) setIsPairing(false);
      }
    };

    pairDevice();
    return () => { isMounted = false; };
  }, [token]);

  const reportProgress = useCallback(async () => {
    if (!sessionId || !token) return;
    try {
      const apiBase = getApiBase();
      await fetch(`${apiBase}/diagnosis/sessions/${sessionId}/progress`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token }),
      });
    } catch (e) {
      console.warn('Progress report error:', e);
    }
  }, [sessionId, token]);

  const submitResults = useCallback(async (tests: any[]) => {
    if (!sessionId || !token) {
      throw new Error('Not connected to a SafeResale session. Please ensure token is valid.');
    }
    const apiBase = getApiBase();
    const deviceInfo = {
      model: navigator.userAgent.slice(0, 80),
      platform: navigator.platform,
      device_type: 'laptop',
    };

    const res = await fetch(`${apiBase}/diagnosis/sessions/${sessionId}/results`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        token,
        tests,
        category: 'laptop',
        device: deviceInfo,
      }),
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err?.detail?.message || `Failed to submit results (${res.status})`);
    }

    const data = await res.json();
    return {
      score: Math.round(data.diagnostic_score ?? 0),
      received: !!data.received,
    };
  }, [sessionId, token]);

  return (
    <PairingContext.Provider
      value={{
        token,
        sessionId,
        listingId,
        isPaired,
        isPairing,
        pairingError,
        reportProgress,
        submitResults,
      }}
    >
      {children}
    </PairingContext.Provider>
  );
}

export function usePairing() {
  const ctx = useContext(PairingContext);
  if (!ctx) throw new Error('usePairing must be used within PairingProvider');
  return ctx;
}
