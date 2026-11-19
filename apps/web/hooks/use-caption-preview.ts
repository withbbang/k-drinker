import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";

import { apiFetch } from "@/lib/api";
import type {
  CaptionPreviewRequest,
  CaptionPreviewResponse,
} from "@/types/api/post";

const DEBOUNCE_MS = 400;

// 입력이 멈춘 뒤에만 서버에 미리보기를 요청한다. 새 데이터를 기다리는 동안은 이전 결과를 보여 준다.
export function useCaptionPreview(id: string, input: CaptionPreviewRequest) {
  const serialized = JSON.stringify(input);
  const [debounced, setDebounced] = useState(input);

  useEffect(() => {
    const timer = setTimeout(
      () => setDebounced(JSON.parse(serialized) as CaptionPreviewRequest),
      DEBOUNCE_MS,
    );
    return () => clearTimeout(timer);
  }, [serialized]);

  const { data, isFetching } = useQuery({
    queryKey: ["posts", id, "caption-preview", debounced],
    queryFn: ({ signal }) =>
      apiFetch<CaptionPreviewResponse>(`/api/v1/posts/${id}/caption-preview`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(debounced),
        signal,
      }),
    placeholderData: keepPreviousData,
  });
  // 입력이 아직 반영 전이면 흐리게 보이도록 대기 중인 것도 가져오는 중으로 본다.
  return {
    data,
    isFetching: isFetching || serialized !== JSON.stringify(debounced),
  };
}
