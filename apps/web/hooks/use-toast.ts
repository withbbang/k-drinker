"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export const TOAST_MS = 5000;

export type ToastAction = { label: string; onAction: () => void };
export type ToastState = { id: number; message: string; action?: ToastAction };

export function useToast() {
  const [toast, setToast] = useState<ToastState | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const nextId = useRef(0);

  const clearTimer = () => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
  };
  const dismiss = useCallback(() => {
    clearTimer();
    setToast(null);
  }, []);
  const show = useCallback((message: string, action?: ToastAction) => {
    clearTimer();
    nextId.current += 1;
    setToast({ id: nextId.current, message, action });
    timer.current = setTimeout(() => {
      timer.current = null;
      setToast(null);
    }, TOAST_MS);
  }, []);
  useEffect(() => clearTimer, []);

  return { toast, show, dismiss };
}
