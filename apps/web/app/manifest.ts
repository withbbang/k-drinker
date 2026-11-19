import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "k-drinker",
    short_name: "k-drinker",
    start_url: "/",
    display: "standalone",
    background_color: "#1D1713",
    theme_color: "#1D1713",
    icons: [
      { src: "/icon/192", sizes: "192x192", type: "image/png" },
      { src: "/icon/512", sizes: "512x512", type: "image/png" },
    ],
    share_target: {
      action: "/share-target",
      method: "POST",
      enctype: "multipart/form-data",
      params: {
        files: [
          { name: "photos", accept: ["image/jpeg", "image/png", "image/*"] },
        ],
      },
    },
  };
}
