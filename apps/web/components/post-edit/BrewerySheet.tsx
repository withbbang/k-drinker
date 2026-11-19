"use client";

import { useEffect, useRef } from "react";

import { BreweryForm } from "@/components/breweries/BreweryForm";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { buttonClass } from "@/components/ui/button";
import type { BreweryResponse } from "@/types/api/brewery";

type BrewerySheetProps = {
  brewery: BreweryResponse;
  onClose: () => void;
};

// 양조장 정보를 고치는 시트. 고친 값은 바로 서버에 저장되고 수정 화면의 되돌리기 기록에는 넣지 않는다.
export function BrewerySheet({ brewery, onClose }: BrewerySheetProps) {
  const body = useRef<HTMLDivElement>(null);
  // BottomSheet는 포커스를 옮기지 않는다. 열리면 첫 입력칸으로 보낸다.
  // 자식인 BottomSheet의 effect가 먼저 돌아 열기 전 포커스(닫힐 때 돌아갈 곳)를 기억한 뒤에 옮긴다.
  useEffect(() => {
    body.current?.querySelector<HTMLElement>("input")?.focus();
  }, []);
  return (
    <BottomSheet title="양조장 정보 고치기" onClose={onClose}>
      <div ref={body} className="flex flex-col gap-3">
        <p className="text-sm text-muted">
          이 양조장을 쓰는 다른 게시물에도 반영돼요
        </p>
        <BreweryForm
          brewery={brewery}
          onSaved={() => onClose()}
          barClassName="bg-surface"
        />
        <button
          type="button"
          onClick={onClose}
          className={buttonClass("secondary")}
        >
          닫기
        </button>
      </div>
    </BottomSheet>
  );
}
