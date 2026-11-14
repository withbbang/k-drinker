import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 배포 이미지는 .next/standalone 의 server.js 만 실행한다 (deploy/compose.yml).
  output: "standalone",
  experimental: {
    // /api/* 재작성 프록시는 요청 본문을 메모리에 버퍼링하며 기본 10MB에서 잘라 버린다.
    // 사진 업로드(multipart)는 최대 210MB(apps/api application.yml의 max-request-size)라 여유 있게 올린다.
    proxyClientMaxBodySize: "250mb",
  },
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  // 값은 next build 시점에 routes manifest로 고정된다.
  async rewrites() {
    const apiInternalUrl = process.env.API_INTERNAL_URL;
    if (!apiInternalUrl) {
      throw new Error(
        "API_INTERNAL_URL 이 설정되지 않았습니다. 예: API_INTERNAL_URL=http://localhost:8080",
      );
    }
    return [
      {
        source: "/api/:path*",
        destination: `${apiInternalUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
