"use client";

import { useCallback } from "react";

// File 미리보기. ref 콜백에서 URL을 만들고 정리 함수로 해제한다 (StrictMode 안전).
export function BlobImage({
  file,
  alt,
  className,
}: {
  file: File;
  alt: string;
  className?: string;
}) {
  const attach = useCallback(
    (img: HTMLImageElement) => {
      const url = URL.createObjectURL(file);
      img.src = url;
      return () => URL.revokeObjectURL(url);
    },
    [file],
  );
  return (
    // eslint-disable-next-line @next/next/no-img-element -- blob 미리보기
    <img ref={attach} alt={alt} className={className} />
  );
}
