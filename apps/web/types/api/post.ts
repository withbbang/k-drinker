import type { BreweryRequest, BreweryResponse } from "@/types/api/brewery";

export type PostChannel = "TELEGRAM" | "WEB";

export type FitMode = "CROP" | "PAD";

export type PostCreateResponse = {
  id: number;
  status: string;
};

export type PostSummaryResponse = {
  id: number;
  status: string;
  drinkName: string | null;
  rating: number | null;
  thumbnailUrl: string | null;
  permalink: string | null;
  channel: PostChannel;
  createdAt: string;
};

export type PostPhotoResponse = {
  id: number;
  position: number;
  url: string;
  fitMode: FitMode;
};

export type PostDetailResponse = {
  id: number;
  status: string;
  version: number;
  editable: boolean;
  failedStage: string | null;
  failureReason: string | null;
  drinkId: number | null;
  drinkName: string | null;
  brewery: BreweryResponse | null;
  rating: number | null;
  memo: string | null;
  captionHead: string | null;
  breweryBlock: string | null;
  captionTail: string | null;
  captionEdited: boolean;
  captionLength: number;
  photos: PostPhotoResponse[];
  permalink: string | null;
  channel: PostChannel;
};

export type PhotoEdit = {
  id: number;
  fitMode: FitMode;
};

export type PostUpdateRequest = {
  version: number;
  drinkId: number | null;
  newDrinkName: string | null;
  breweryId: number | null;
  breweryEdit: BreweryRequest | null;
  rating: number | null;
  memo: string | null;
  captionHead: string | null;
  captionTail: string | null;
  photos: PhotoEdit[];
};
