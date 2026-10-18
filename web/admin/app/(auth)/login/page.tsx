"use client";

import { useState } from "react";
import { Loader2, ShieldCheck } from "lucide-react";
import { signInWithEmailAndPassword, signInWithPopup, GoogleAuthProvider, getIdToken } from "firebase/auth";
import { getFirebaseAuth, isFirebaseConfigured } from "@/lib/firebase";
import { post, storeTokens, clearSession } from "@/lib/api";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export default function LoginPage() {
  const fbReady = isFirebaseConfigured();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");

  function redirect(userRole?: string) {
    if (userRole === "inspector") {
      window.location.href = "/inspections";
    } else {
      window.location.href = "/dashboard";
    }
  }

  async function exchangeIdToken(idToken: string) {
    const data = await post("/auth/firebase", { id_token: idToken });
    storeTokens(data);
    redirect(data?.user?.role);
  }

  async function onEmailLogin(e: React.FormEvent) {
    e.preventDefault();
    setErr("");
    setBusy(true);
    try {
      if (fbReady) {
        const cred = await signInWithEmailAndPassword(getFirebaseAuth(), email, password);
        const tok = await getIdToken(cred.user);
        await exchangeIdToken(tok);
      } else {
        const data = await post("/auth/login", { email, password });
        storeTokens(data);
        redirect(data?.user?.role);
      }
    } catch (ex: any) {
      setErr(ex?.message || String(ex));
      setBusy(false);
    }
  }

  async function onGoogleLogin() {
    setErr("");
    setBusy(true);
    try {
      const provider = new GoogleAuthProvider();
      clearSession(true);
      const cred = await signInWithPopup(getFirebaseAuth(), provider);
      const tok = await getIdToken(cred.user);
      await exchangeIdToken(tok);
    } catch (ex: any) {
      setErr(ex?.message || String(ex));
      setBusy(false);
    }
  }

  return (
    <div className="w-full max-w-sm">
      <div className="mb-8 flex flex-col items-center gap-3 text-center">
        <span className="flex size-12 items-center justify-center rounded-2xl bg-accent text-accent-foreground shadow-sm">
          <ShieldCheck className="size-7" strokeWidth={2.5} />
        </span>
        <div className="space-y-1">
          <h1 className="text-xl font-bold tracking-tight">
            SafeResale <span className="bg-accent text-accent-foreground px-1.5 py-0.5 rounded-lg">Portal</span>
          </h1>
          <p className="text-sm text-muted-foreground">Admin & Certified Inspector console</p>
        </div>
      </div>

      <Card className="rounded-2xl ring-1 ring-black/5 dark:ring-white/10">
        <CardHeader className="pb-2">
          <CardTitle className="text-base">Sign in</CardTitle>
          <CardDescription>
            {fbReady ? "Use your Firebase account or Google." : "Dev mode — sign in with Admin or Inspector credentials."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {err && (
            <div className="mb-4 rounded-xl border border-destructive/20 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {err}
            </div>
          )}

          {/* Quick Demo Credentials */}
          <div className="mb-4 flex items-center gap-2">
            <button
              type="button"
              onClick={() => {
                setEmail("inspector@gmail.com");
                setPassword("Rahul@980");
              }}
              className="text-[11px] font-medium px-2.5 py-1 rounded-full bg-primary/10 text-primary hover:bg-primary/20 transition-colors border border-primary/20 cursor-pointer"
            >
              🔍 Quick Inspector
            </button>
            <button
              type="button"
              onClick={() => {
                setEmail("rahul.legend345@gmail.com");
                setPassword("Rahul@980");
              }}
              className="text-[11px] font-medium px-2.5 py-1 rounded-full bg-muted hover:bg-muted/80 text-muted-foreground transition-colors border border-border cursor-pointer"
            >
              🛡️ Quick Admin
            </button>
          </div>

          <form className="space-y-4" onSubmit={onEmailLogin}>
            <div className="space-y-1.5">
              <Label>Email</Label>
              <Input type="email" placeholder="inspector@gmail.com" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
            </div>
            <div className="space-y-1.5">
              <Label>Password</Label>
              <Input type="password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
            </div>
            <Button type="submit" className="w-full cursor-pointer" disabled={busy}>
              {busy ? <Loader2 className="size-4 animate-spin" /> : null}
              {busy ? "Signing in..." : "Sign in"}
            </Button>
          </form>

          {fbReady && (
            <>
              <div className="my-4 flex items-center gap-3 text-xs text-muted-foreground">
                <span className="h-px flex-1 bg-border" />
                or
                <span className="h-px flex-1 bg-border" />
              </div>
              <Button variant="secondary" className="w-full" disabled={busy} onClick={onGoogleLogin}>
                Continue with Google
              </Button>
            </>
          )}

          {!fbReady && (
            <p className="mt-4 text-xs text-muted-foreground">
              No Firebase config found (<code className="text-foreground">NEXT_PUBLIC_FIREBASE_*</code>). Add it to{" "}
              <code className="text-foreground">.env.local</code> to enable Google sign-in.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}