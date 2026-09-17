"use client";

import { useState } from "react";
import { ShieldCheck } from "lucide-react";
import { signInWithEmailAndPassword, signInWithPopup, GoogleAuthProvider, getIdToken } from "firebase/auth";
import { getFirebaseAuth, isFirebaseConfigured } from "@/lib/firebase";
import { post, storeTokens, clearSession } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";

export default function LoginPage() {
  const fbReady = isFirebaseConfigured();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");

  function redirect() {
    window.location.href = "/dashboard";
  }

  async function exchangeIdToken(idToken: string) {
    const data = await post("/auth/firebase", { id_token: idToken });
    storeTokens(data);
    redirect();
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
        redirect();
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
        <span className="flex size-12 items-center justify-center rounded-xl bg-primary text-primary-foreground">
          <ShieldCheck className="size-7" strokeWidth={2.5} />
        </span>
        <div className="space-y-1">
          <h1 className="text-xl font-bold tracking-tight">
            SafeResale <span className="text-primary">Admin</span>
          </h1>
          <p className="text-sm text-muted-foreground">Verification Trust Engine console</p>
        </div>
      </div>

      <Card className="border-0 bg-card shadow-sm">
        <CardHeader className="pb-4">
          <CardTitle className="text-base">Sign in</CardTitle>
          <CardDescription>
            {fbReady ? "Use your Firebase admin account or Google." : "Dev mode — backend credentials."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {err && (
            <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {err}
            </div>
          )}

          <form className="space-y-4" onSubmit={onEmailLogin}>
            <div className="space-y-1.5">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" required autoComplete="email" placeholder="admin@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">Password</Label>
              <Input id="password" type="password" required autoComplete="current-password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} />
            </div>
            <Button type="submit" className="w-full" disabled={busy}>
              {busy ? <Spinner className="size-4" /> : "Sign in"}
            </Button>
          </form>

          {fbReady && (
            <>
              <div className="my-4 flex items-center gap-3 text-xs text-muted-foreground">
                <span className="h-px flex-1 bg-border" />
                or
                <span className="h-px flex-1 bg-border" />
              </div>
              <Button variant="outline" className="w-full" disabled={busy} onClick={onGoogleLogin}>
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