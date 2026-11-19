import { Gowun_Batang } from "next/font/google";
import localFont from "next/font/local";

export const pretendard = localFont({
  src: "../node_modules/pretendard/dist/web/variable/woff2/PretendardVariable.woff2",
  variable: "--font-pretendard",
  display: "swap",
  weight: "45 920",
});

export const gowunBatang = Gowun_Batang({
  weight: ["400", "700"],
  subsets: ["latin"],
  variable: "--font-gowun",
  display: "swap",
  // 한글 조각 파일이 수십 개라 미리 받기를 켜면 응답의 Link 헤더가 6KB에 가까워진다.
  // NAS 역방향 프록시(nginx 기본 버퍼 4KB)가 "upstream sent too big header"로 502를 낸다.
  // 술 이름에만 쓰는 글꼴이라 미리 받지 않는다.
  preload: false,
});
