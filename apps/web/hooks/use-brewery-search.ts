import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";

import { apiFetch } from "@/lib/api";
import type { BreweryResponse } from "@/types/api/brewery";
import type { PageResponse } from "@/types/api/common";

const DEBOUNCE_MS = 300;

// 입력이 멈춘 뒤에만 검색한다. 한글을 조합하는 중간 글자마다 요청과 안내가 나가지 않게 한다.
// waiting이 true면 지금 입력에 대한 결과가 아직 없다는 뜻이라 결과·안내를 단정하지 않는다.
export function useBrewerySearch(q: string) {
  const term = q.trim();
  const [debounced, setDebounced] = useState(term);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(term), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [term]);

  const query = useQuery({
    queryKey: ["breweries", debounced],
    queryFn: () =>
      apiFetch<PageResponse<BreweryResponse>>(
        `/api/v1/breweries?q=${encodeURIComponent(debounced)}&page=0&size=20`,
      ),
    enabled: debounced !== "",
  });

  return { query, waiting: term !== debounced };
}
