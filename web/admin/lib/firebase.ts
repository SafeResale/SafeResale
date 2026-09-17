"use client";

import { initializeApp, getApps, type FirebaseApp } from "firebase/app";
import { getAuth, type Auth } from "firebase/auth";

/**
 * SafeResale admin browser-side Firebase init.
 *
 * Config comes from NEXT_PUBLIC_FIREBASE_* env vars (the public web/browser
 * configuration from the Firebase console — NOT the server service-account
 * JSON, which lives only in the backend and is gitignored). When the env vars
 * are absent (CI / not-yet-configured), isFirebaseConfigured() is false and the
 * login page falls back to the dev email/password exchange on POST /auth/login.
 */

export const FIREBASE_CONFIG = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY || "",
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN || "",
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID || "",
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET || "",
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID || "",
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID || "",
} as const;

export function isFirebaseConfigured(): boolean {
  return !!FIREBASE_CONFIG.apiKey && !!FIREBASE_CONFIG.authDomain && !!FIREBASE_CONFIG.projectId;
}

let _app: FirebaseApp | null = null;
let _auth: Auth | null = null;

export function getFirebaseApp(): FirebaseApp {
  if (!isFirebaseConfigured()) {
    throw new Error("Firebase browser config not set (NEXT_PUBLIC_FIREBASE_*). Cannot init auth.");
  }
  if (!_app) {
    _app = getApps().length ? getApps()[0]! : initializeApp(FIREBASE_CONFIG);
  }
  return _app;
}

export function getFirebaseAuth(): Auth {
  if (!_auth) {
    _auth = getAuth(getFirebaseApp());
  }
  return _auth;
}