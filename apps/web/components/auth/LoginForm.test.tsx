import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { LoginForm } from "@/components/auth/LoginForm";
import { QueryProvider } from "@/components/providers/QueryProvider";

const fetchMock = vi.fn();
const { replaceMock, searchParamsRef } = vi.hoisted(() => ({
  replaceMock: vi.fn(),
  searchParamsRef: { current: new URLSearchParams() },
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/login",
  useRouter: () => ({ replace: replaceMock }),
  useSearchParams: () => searchParamsRef.current,
}));

function renderForm() {
  render(
    <QueryProvider>
      <LoginForm />
    </QueryProvider>,
  );
  return {
    input: screen.getByRole("textbox", { name: "인증 코드" }),
    submit: screen.getByRole("button", { name: "로그인" }),
  };
}

async function loginWith(next: string | null) {
  searchParamsRef.current = new URLSearchParams(next === null ? "" : { next });
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
  const { input } = renderForm();
  await userEvent.type(input, "123456");
}

describe("LoginForm", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    searchParamsRef.current = new URLSearchParams();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    replaceMock.mockReset();
  });

  it("submits the six-digit code and moves to home", async () => {
    // when
    await loginWith(null);

    // then
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/"));
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/sessions",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ code: "123456" }),
      }),
    );
  });

  it("redirects to next after login", async () => {
    // when
    await loginWith("/share");

    // then
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/share"));
  });

  it.each([["//evil.com"], ["https://x"]])(
    "ignores external next %s",
    async (next) => {
      // when
      await loginWith(next);

      // then
      await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/"));
      expect(replaceMock).toHaveBeenCalledTimes(1);
    },
  );

  it("shows the server message when the code is wrong", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          error: {
            code: "AUTH_INVALID_CODE",
            message: "OTP 코드가 올바르지 않습니다.",
          },
        }),
        { status: 400 },
      ),
    );
    const { input } = renderForm();

    // when
    await userEvent.type(input, "000000");

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "OTP 코드가 올바르지 않습니다.",
    );
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it("keeps six digits when a code with a space is pasted", async () => {
    // given
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const { input } = renderForm();

    // when
    await userEvent.click(input);
    await userEvent.paste("123 456");

    // then
    expect(input).toHaveValue("123456");
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
  });

  it("drops digits past the sixth", async () => {
    // given
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const { input } = renderForm();

    // when
    await userEvent.click(input);
    await userEvent.paste("1234567");

    // then
    expect(input).toHaveValue("123456");
  });

  it("disables submit until six digits are entered", async () => {
    // given
    fetchMock.mockReturnValue(new Promise(() => {}));
    const { input, submit } = renderForm();

    // when, then
    expect(input).toHaveAttribute("inputmode", "numeric");
    expect(input).toHaveAttribute("autocomplete", "one-time-code");
    await userEvent.type(input, "12345");
    expect(submit).toBeDisabled();

    await userEvent.type(input, "6");
    // 6자리가 차면 자동으로 요청해 요청 중에는 다시 비활성이 된다.
    await waitFor(() => expect(submit).toBeDisabled());
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("코드_입력칸은_어두운_토큰_색을_쓴다", () => {
    const { input } = renderForm();
    expect(input).not.toHaveClass("bg-white");
    expect(input).toHaveClass("bg-surface", "text-text");
  });

  it("6자리를_채우면_바로_요청한다", async () => {
    // given
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const { input } = renderForm();

    // when
    await userEvent.type(input, "123456");

    // then
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/sessions",
      expect.objectContaining({ body: JSON.stringify({ code: "123456" }) }),
    );
  });

  it("자동_요청_뒤_Enter를_눌러도_한_번만_보낸다", async () => {
    // given
    fetchMock.mockReturnValue(new Promise(() => {}));
    const { input } = renderForm();

    // when
    await userEvent.type(input, "123456{Enter}");

    // then
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("실패한_뒤_지우고_다시_채우면_다시_요청한다", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          error: { code: "AUTH_INVALID_CODE", message: "틀렸어요." },
        }),
        { status: 400 },
      ),
    );
    const { input } = renderForm();
    await userEvent.type(input, "123456");
    expect(await screen.findByRole("alert")).toBeInTheDocument();

    // when
    await userEvent.type(input, "{Backspace}7");

    // then
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(fetchMock).toHaveBeenLastCalledWith(
      "/api/v1/sessions",
      expect.objectContaining({ body: JSON.stringify({ code: "123457" }) }),
    );
  });
});
