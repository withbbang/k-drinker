import type { ReactNode } from "react";

import type { Metadata, Viewport } from "next";

import { ServiceWorkerRegister } from "@/components/pwa/ServiceWorkerRegister";
import { QueryProvider } from "@/components/providers/QueryProvider";

import "./globals.css";

export const metadata: Metadata = {
  title: "k-drinker",
  description: "전통주 후기 SNS 자동화 업로드",
};

export const viewport: Viewport = {
  themeColor: "#171717",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body className="bg-neutral-50 text-neutral-900 antialiased">
        <ServiceWorkerRegister />
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
