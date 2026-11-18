import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { PostEditForm } from "@/components/post-edit/PostEditForm";
import { QueryProvider } from "@/components/providers/QueryProvider";
import { ApiError } from "@/lib/api";
import type { PostDetailResponse } from "@/types/api/post";

const { apiFetchMock, pushMock } = vi.hoisted(() => ({
  apiFetchMock: vi.fn(),
  pushMock: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/posts/7/edit",
  useRouter: () => ({ replace: vi.fn(), push: pushMock }),
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  apiFetch: apiFetchMock,
}));

const brewery = {
  id: 5,
  name: "국순당",
  address: "서울",
  phone: null,
  homepage: null,
  instagramHandle: null,
  otherSns: null,
  source: "MANUAL",
  verified: false,
};

function post(overrides: Partial<PostDetailResponse> = {}): PostDetailResponse {
  return {
    id: 7,
    status: "AWAITING_CONFIRMATION",
    version: 4,
    editable: true,
    failedStage: null,
    failureReason: null,
    drinkId: 9,
    drinkName: "막걸리",
    brewery,
    rating: 4.5,
    memo: "좋음",
    captionHead: "머리 문장",
    breweryBlock: "국순당\n서울",
    captionTail: "#막걸리",
    captionEdited: false,
    captionLength: 123,
    photos: [
      { id: 1, position: 0, url: "/m/1.jpg", fitMode: "CROP" },
      { id: 2, position: 1, url: "/m/2.jpg", fitMode: "CROP" },
    ],
    permalink: null,
    channel: "WEB",
    publishCaption: "",
    retryable: false,
    ...overrides,
  };
}

function setup(detail: PostDetailResponse) {
  apiFetchMock.mockImplementation(async (path: string, init?: RequestInit) => {
    if (path === "/api/v1/posts/7" && init?.method === "PUT") return detail;
    if (path === "/api/v1/posts/7") return detail;
    return undefined;
  });
  render(
    <QueryProvider>
      <PostEditForm id="7" />
    </QueryProvider>,
  );
}

function putCalls(): FormData[] {
  return apiFetchMock.mock.calls
    .filter(([, init]) => init?.method === "PUT")
    .map(([, init]) => init.body as FormData);
}

// multipart의 request 파트(JSON)를 읽는다.
async function requestOf(form: FormData) {
  return JSON.parse(await (form.get("request") as Blob).text());
}

async function putBody() {
  const [form] = putCalls();
  if (!form) throw new Error("PUT 호출 없음");
  return requestOf(form);
}

const photoFile = (name: string) =>
  new File(["x"], name, { type: "image/jpeg" });

async function addPhoto(file: File) {
  const input = document.querySelector<HTMLInputElement>('input[type="file"]')!;
  await userEvent.upload(input, file);
}

async function confirmDelete(index: number) {
  await userEvent.click(
    (await screen.findAllByRole("button", { name: "삭제" }))[index],
  );
  await userEvent.click(screen.getByRole("button", { name: "빼기" }));
}

describe("PostEditForm", () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
    pushMock.mockReset();
    // 사진 버튼 애니메이션 없이 onChange가 즉시 호출되게 한다
    vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: true }));
    URL.createObjectURL = vi.fn((file: Blob) => `blob:${(file as File).name}`);
    URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("fills the form from the post", async () => {
    setup(post());
    expect(await screen.findByLabelText("메모")).toHaveValue("좋음");
    expect(screen.getByLabelText("캡션 머리")).toHaveValue("머리 문장");
    expect(screen.getByLabelText("캡션 꼬리")).toHaveValue("#막걸리");
    expect(screen.getByText("선택한 술: 막걸리")).toBeInTheDocument();
    expect(screen.getByText(/캡션 123자 \/ 2,200자/)).toBeInTheDocument();
  });

  it("sends version and photo order on save", async () => {
    setup(post());
    await userEvent.click(
      (await screen.findAllByRole("button", { name: "아래로" }))[0],
    );
    await userEvent.clear(screen.getByLabelText("캡션 머리"));
    await userEvent.type(screen.getByLabelText("캡션 머리"), "새 머리");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(async () => expect((await putBody()).version).toBe(4));
    const body = await putBody();
    expect(body.photos).toEqual([
      { id: 2, fitMode: "CROP" },
      { id: 1, fitMode: "CROP" },
    ]);
    expect(body.captionHead).toBe("새 머리");
    expect(body.captionTail).toBe("#막걸리");
    expect(body.drinkId).toBe(9);
    expect(body.newDrinkName).toBeNull();
    expect(body.breweryId).toBeNull();
    expect(body.breweryEdit).toBeNull();
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/posts/7"));
    expect(screen.queryByText(/텔레그램으로 새 개요/)).toBeNull();
  });

  it("locks photo controls and rating while saving", async () => {
    // given: 저장 요청이 끝나지 않는다
    const detail = post();
    apiFetchMock.mockImplementation((path: string, init?: RequestInit) =>
      init?.method === "PUT"
        ? new Promise(() => undefined)
        : Promise.resolve(path === "/api/v1/posts/7" ? detail : undefined),
    );
    render(
      <QueryProvider>
        <PostEditForm id="7" />
      </QueryProvider>,
    );

    // when
    await userEvent.click(await screen.findByRole("button", { name: "저장" }));

    // then
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "저장" })).toBeDisabled(),
    );
    for (const name of ["위로", "아래로", "삭제", "여백 넣기"]) {
      for (const button of screen.getAllByRole("button", { name })) {
        expect(button).toBeDisabled();
      }
    }
    expect(screen.getByRole("button", { name: "사진 추가" })).toBeDisabled();
    expect(screen.getByLabelText("별점")).toBeDisabled();
  });

  it("disables save while a photo animation is pending, then saves the deletion", async () => {
    // 애니메이션을 켠다(모션 감소 아님)
    vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: false }));
    setup(post());
    await confirmDelete(0);

    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "저장" })).toBeEnabled(),
    );
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(async () =>
      expect((await putBody()).photos).toEqual([{ id: 2, fitMode: "CROP" }]),
    );
  });

  it("sends brewery edit only when a field changed", async () => {
    setup(post());
    await userEvent.type(await screen.findByLabelText("전화"), "02-123");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(async () =>
      expect((await putBody()).breweryEdit).not.toBeNull(),
    );
    expect((await putBody()).breweryEdit).toEqual({
      name: "국순당",
      address: "서울",
      phone: "02-123",
      homepage: null,
      instagramHandle: null,
      otherSns: null,
    });
  });

  it("shows the conflict message", async () => {
    setup(post());
    apiFetchMock.mockImplementation(
      async (_path: string, init?: RequestInit) => {
        if (init?.method === "PUT") {
          throw new ApiError(
            "POST_VERSION_CONFLICT",
            "다른 곳에서 먼저 바뀌었습니다.",
          );
        }
        return post();
      },
    );
    await userEvent.click(await screen.findByRole("button", { name: "저장" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "다른 곳에서 먼저 바뀌었습니다.",
    );
  });

  it("shows a read-only notice when not editable", async () => {
    setup(post({ editable: false, status: "PUBLISHED" }));
    expect(
      await screen.findByText("지금은 수정할 수 없는 게시물입니다."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "저장" })).toBeNull();
  });

  it("sends newDrinkName instead of drinkId for a new drink", async () => {
    setup(post());
    await userEvent.type(await screen.findByLabelText("새 이름으로"), "새 술");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(async () =>
      expect((await putBody()).newDrinkName).toBe("새 술"),
    );
    expect((await putBody()).drinkId).toBeNull();
  });

  it("disables brewery editing until a drink is chosen", async () => {
    setup(post({ drinkId: null, drinkName: null, brewery: null }));
    expect(
      await screen.findByText("술을 먼저 정해 주세요"),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("양조장 검색")).toBeDisabled();
  });

  const other = {
    ...brewery,
    id: 6,
    name: "배상면주가",
    address: "경기",
    phone: "031-1",
  };

  function setupSearch(initial: PostDetailResponse) {
    let detail = initial;
    apiFetchMock.mockImplementation(
      async (path: string, init?: RequestInit) => {
        if (path === "/api/v1/breweries/6") return other;
        if (path?.startsWith("/api/v1/breweries?")) {
          return {
            items: [other],
            page: 0,
            size: 20,
            totalElements: 1,
            totalPages: 1,
          };
        }
        if (path?.startsWith("/api/v1/drinks?")) {
          return [
            { id: 11, name: "청주", breweryId: 6, breweryName: "배상면주가" },
            { id: 12, name: "탁주", breweryId: null, breweryName: null },
          ];
        }
        if (init?.method === "PUT") {
          detail = { ...detail, version: detail.version + 1 };
        }
        return detail;
      },
    );
    render(
      <QueryProvider>
        <PostEditForm id="7" />
      </QueryProvider>,
    );
  }

  function putBodies() {
    return Promise.all(putCalls().map(requestOf));
  }

  it("does not send breweryId or breweryEdit after clearing the drink", async () => {
    setupSearch(post({ drinkId: null, drinkName: null, brewery: null }));
    await userEvent.type(await screen.findByLabelText("새 이름으로"), "새 술");
    await userEvent.type(screen.getByLabelText("양조장 검색"), "배");
    await userEvent.click(
      await screen.findByRole("option", { name: "배상면주가" }),
    );
    await userEvent.type(screen.getByLabelText("전화"), "9");
    await userEvent.clear(screen.getByLabelText("새 이름으로"));
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(putCalls()).toHaveLength(1));
    expect((await putBodies())[0].breweryId).toBeNull();
    expect((await putBodies())[0].breweryEdit).toBeNull();
  });

  it("uses the version from the previous save response", async () => {
    setupSearch(post());
    await userEvent.click(await screen.findByRole("button", { name: "저장" }));
    await waitFor(() => expect(putCalls()).toHaveLength(1));
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/posts/7"));
    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(putCalls()).toHaveLength(2));
    expect((await putBodies()).map((b) => b.version)).toEqual([4, 5]);
  });

  it("bases brewery edit on the newly picked brewery", async () => {
    setupSearch(post());
    await userEvent.type(await screen.findByLabelText("양조장 검색"), "배");
    await userEvent.click(
      await screen.findByRole("option", { name: "배상면주가" }),
    );
    expect(screen.getByLabelText("주소")).toHaveValue("경기");
    expect(screen.getByLabelText("전화")).toHaveValue("031-1");
    await userEvent.type(screen.getByLabelText("홈페이지"), "x.kr");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(putCalls()).toHaveLength(1));
    expect((await putBodies())[0].breweryId).toBe(6);
    expect((await putBodies())[0].breweryEdit).toEqual({
      name: "배상면주가",
      address: "경기",
      phone: "031-1",
      homepage: "x.kr",
      instagramHandle: null,
      otherSns: null,
    });
  });

  it("sends drinkId of a searched drink without newDrinkName", async () => {
    setupSearch(post());
    await userEvent.type(await screen.findByLabelText("술 검색"), "청");
    await userEvent.click(await screen.findByRole("option", { name: /청주/ }));
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(putCalls()).toHaveLength(1));
    expect((await putBodies())[0].drinkId).toBe(11);
    expect((await putBodies())[0].newDrinkName).toBeNull();
  });

  it("shows the picked drink's brewery and bases brewery edit on it", async () => {
    setupSearch(post());
    await userEvent.type(await screen.findByLabelText("술 검색"), "청");
    await userEvent.click(await screen.findByRole("option", { name: /청주/ }));

    expect(
      await screen.findByText("선택한 양조장: 배상면주가"),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("전화")).toHaveValue("031-1");
    await userEvent.type(screen.getByLabelText("홈페이지"), "x.kr");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(putCalls()).toHaveLength(1));
    expect((await putBodies())[0].drinkId).toBe(11);
    expect((await putBodies())[0].breweryId).toBeNull();
    expect((await putBodies())[0].breweryEdit).toEqual({
      name: "배상면주가",
      address: "경기",
      phone: "031-1",
      homepage: "x.kr",
      instagramHandle: null,
      otherSns: null,
    });
  });

  it("shows no brewery after picking a drink without one", async () => {
    setupSearch(post());
    await userEvent.type(await screen.findByLabelText("술 검색"), "탁");
    await userEvent.click(await screen.findByRole("option", { name: "탁주" }));

    expect(
      await screen.findByText("정해진 양조장이 없습니다."),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("전화")).toBeNull();
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(putCalls()).toHaveLength(1));
    expect((await putBodies())[0].drinkId).toBe(12);
    expect((await putBodies())[0].breweryEdit).toBeNull();
  });

  it("keeps the current brewery for a new drink name", async () => {
    setup(post());
    await userEvent.type(await screen.findByLabelText("새 이름으로"), "새 술");

    expect(screen.getByText("선택한 양조장: 국순당")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("전화"), "02-9");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(async () =>
      expect((await putBody()).newDrinkName).toBe("새 술"),
    );
    expect((await putBody()).drinkId).toBeNull();
    expect((await putBody()).breweryId).toBeNull();
    expect((await putBody()).breweryEdit).toEqual({
      name: "국순당",
      address: "서울",
      phone: "02-9",
      homepage: null,
      instagramHandle: null,
      otherSns: null,
    });
  });

  it("새_사진을_더해_저장하면_multipart로_보낸다", async () => {
    // given
    setup(post());
    await screen.findAllByRole("button", { name: "삭제" });
    const file = photoFile("new.jpg");
    await addPhoto(file);

    // when
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    // then
    await waitFor(() => expect(putCalls()).toHaveLength(1));
    const form = putCalls()[0];
    expect(form).toBeInstanceOf(FormData);
    const [, init] = apiFetchMock.mock.calls.find(
      ([, i]) => i?.method === "PUT",
    )!;
    expect(init.headers).toBeUndefined();
    expect((form.get("request") as Blob).type).toBe("application/json");
    expect((await requestOf(form)).photos).toEqual([
      { id: 1, fitMode: "CROP" },
      { id: 2, fitMode: "CROP" },
      { newIndex: 0, fitMode: "CROP" },
    ]);
    const files = form.getAll("newPhotos");
    expect(files).toHaveLength(1);
    expect((files[0] as File).name).toBe("new.jpg");
  });

  it("저장_실패해도_새_사진과_되돌리기가_남는다", async () => {
    // given
    setup(post());
    apiFetchMock.mockImplementation(
      async (_path: string, init?: RequestInit) => {
        if (init?.method === "PUT") {
          throw new ApiError(
            "POST_VERSION_CONFLICT",
            "다른 곳에서 먼저 바뀌었습니다.",
          );
        }
        return post();
      },
    );
    await screen.findAllByRole("button", { name: "삭제" });
    await addPhoto(photoFile("new.jpg"));

    // when
    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    await screen.findByRole("alert");

    // then
    expect(screen.getByAltText("사진 3")).toHaveAttribute(
      "src",
      "blob:new.jpg",
    );
    expect(screen.getByRole("button", { name: "되돌리기" })).toBeEnabled();

    // when — 다시 저장
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    // then
    await waitFor(() => expect(putCalls()).toHaveLength(2));
    const [first, second] = putCalls();
    expect(second.get("newPhotos")).toBe(first.get("newPhotos"));
  });

  it("삭제_뒤_되돌리기면_다시_보인다", async () => {
    // given
    setup(post());
    await confirmDelete(0);
    expect(screen.getAllByAltText(/^사진 \d$/)).toHaveLength(1);

    // when
    await userEvent.click(screen.getByRole("button", { name: "되돌리기" }));

    // then
    const srcs = screen
      .getAllByAltText(/^사진 \d$/)
      .map((img) => img.getAttribute("src"));
    expect(srcs).toEqual(["/m/1.jpg?v=4", "/m/2.jpg?v=4"]);
  });

  it("애니메이션_중엔_되돌리기_비활성", async () => {
    // given — 별점을 바꿔 되돌릴 기록을 만든 뒤 애니메이션을 켠다
    setup(post());
    await userEvent.selectOptions(
      await screen.findByRole("combobox", { name: "별점" }),
      "3.0",
    );
    expect(screen.getByRole("button", { name: "되돌리기" })).toBeEnabled();
    vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: false }));
    vi.useFakeTimers();

    try {
      // when
      fireEvent.click(screen.getAllByRole("button", { name: "삭제" })[0]);
      fireEvent.click(screen.getByRole("button", { name: "빼기" }));

      // then
      expect(screen.getByRole("button", { name: "되돌리기" })).toBeDisabled();
      act(() => {
        vi.advanceTimersByTime(250);
      });
      expect(screen.getByRole("button", { name: "되돌리기" })).toBeEnabled();
    } finally {
      vi.useRealTimers();
    }
  });

  it("limits the memo to 2000 characters", async () => {
    setup(post());
    expect(await screen.findByLabelText("메모")).toHaveAttribute(
      "maxLength",
      "2000",
    );
  });
});
