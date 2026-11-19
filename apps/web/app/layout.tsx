import type { ReactNode } from "react";

import type { Metadata, Viewport } from "next";

import { ServiceWorkerRegister } from "@/components/pwa/ServiceWorkerRegister";
import { QueryProvider } from "@/components/providers/QueryProvider";

import "./globals.css";
import { gowunBatang, pretendard } from "./fonts";

export const metadata: Metadata = {
  title: "k-drinker",
  description: "전통주 후기 SNS 자동화 업로드",
};

export const viewport: Viewport = {
  themeColor: "#1D1713",
  viewportFit: "cover",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html
      lang="ko"
      className={`${pretendard.variable} ${gowunBatang.variable}`}
    >
      <body className="bg-bg text-text font-sans antialiased">
        <ServiceWorkerRegister />
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
