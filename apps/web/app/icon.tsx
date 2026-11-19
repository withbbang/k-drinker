import { ImageResponse } from "next/og";

const SIZES = [192, 512] as const;

export function generateImageMetadata() {
  return SIZES.map((size) => ({
    id: String(size),
    size: { width: size, height: size },
    contentType: "image/png",
  }));
}

export default async function Icon({ id }: { id: Promise<string | number> }) {
  const size = Number(await id);
  return new ImageResponse(
    <div
      tw="flex h-full w-full items-center justify-center bg-[#1D1713] font-bold text-[#D6B04C]"
      style={{ fontSize: size * 0.6 }}
    >
      k
    </div>,
    { width: size, height: size },
  );
}
