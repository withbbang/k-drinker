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
  /** 실제로 게시될 캡션(저장된 캡션에서 마커만 뺀 값). 개요 화면은 이 값을 그대로 보여 준다. */
  publishCaption: string;
  /** 서버가 [다시 시도]를 받아 주는 실패인지. false면 [수정]으로 고쳐야 한다. */
  retryable: boolean;
};

// 저장된 사진은 id로, 새 사진은 newPhotos 파트 순서(newIndex)로 가리킨다.
export type PhotoEdit =
  { id: number; fitMode: FitMode } | { newIndex: number; fitMode: FitMode };

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
