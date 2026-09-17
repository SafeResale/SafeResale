"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { get, ApiError } from "./api";

export function useFetch<T>(path: string | null) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(!!path);
  const [error, setError] = useState<ApiError | null>(null);
  const seq = useRef(0);

  const reload = useCallback(async () => {
    if (!path) return;
    const id = ++seq.current;
    setLoading(true);
    try {
      const d = await get<T>(path);
      if (seq.current !== id) return;
      setData(d);
      setError(null);
    } catch (e) {
      if (seq.current !== id) return;
      setError(e instanceof ApiError ? e : new ApiError(0, String(e)));
    } finally {
      if (seq.current === id) setLoading(false);
    }
  }, [path]);

  useEffect(() => {
    reload();
    return () => {
      seq.current++;
    };
  }, [reload]);

  return { data, loading, error, reload, setData };
}

type MutOpts = { success?: string; silent?: boolean };

export function useAction<TArgs extends any[], TResult = any>(fn: (...args: TArgs) => Promise<TResult>) {
  const [pending, setPending] = useState(false);

  const run = useCallback(
    async (...args: TArgs): Promise<TResult | null> => {
      setPending(true);
      try {
        return await fn(...args);
      } catch (e) {
        toast.error(e instanceof ApiError ? e.message : String(e));
        return null;
      } finally {
        setPending(false);
      }
    },
    [fn],
  );

  return { run, pending };
}

/** Run a mutation with pending guard; returns boolean success and toasts result. */
export async function runMutation(fn: () => Promise<any>, opts: MutOpts = {}) {
  try {
    await fn();
    if (opts.success) toast.success(opts.success);
    return true;
  } catch (e) {
    toast.error(e instanceof ApiError ? e.message : String(e));
    return false;
  }
}