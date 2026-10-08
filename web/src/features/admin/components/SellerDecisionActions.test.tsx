import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { SellerDecisionActions } from "./SellerDecisionActions";

/**
 * Admin karar eylemleri (seller-onboarding spec'i "Admin başvuruları karara
 * bağlar"). Ağ ve yönlendirici taklit edilir (AGENTS.md §12.6).
 */

const router = { refresh: vi.fn() };

vi.mock("next/navigation", () => ({ useRouter: () => router }));

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const REASON = "Belgedeki ad ile beyan edilen ad eşleşmiyor.";

/** Diyalogdaki onay düğmesi; tetikleyiciyle aynı adı taşıdığı için türünden bulunur. */
function submitIn(dialog: HTMLElement): HTMLButtonElement {
  const confirm = Array.from(dialog.querySelectorAll("button")).find((button) => button.type === "submit");
  if (confirm === undefined) {
    throw new Error("Onay düğmesi yok");
  }
  return confirm;
}

beforeEach(() => {
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("SellerDecisionActions", () => {
  it("bekleyen kayıtta onayı birincil eylem olarak gönderir ve sayfayı yeniler", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { status: "APPROVED" }));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<SellerDecisionActions sellerId={7} status="PENDING" />);

    expect(screen.getByRole("button", { name: "Reddet" })).toBeDefined();
    fireEvent.click(screen.getByRole("button", { name: "Onayla" }));

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/admin/sellers/7/approve");
    expect(init.method).toBe("POST");
  });

  it("ret penceresi kısa gerekçeyi göndermeden reddeder", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<SellerDecisionActions sellerId={7} status="PENDING" />);

    fireEvent.click(screen.getByRole("button", { name: "Reddet" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.change(screen.getByLabelText("Gerekçe"), { target: { value: "kısa" } });
    fireEvent.click(submitIn(dialog));

    expect(await screen.findByText("Gerekçe en az 10 karakter olmalıdır.")).toBeDefined();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("geçerli gerekçeyle reddi gönderir", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { status: "REJECTED" }));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<SellerDecisionActions sellerId={7} status="PENDING" />);

    fireEvent.click(screen.getByRole("button", { name: "Reddet" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.change(screen.getByLabelText("Gerekçe"), { target: { value: REASON } });
    fireEvent.click(submitIn(dialog));

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/admin/sellers/7/reject");
    expect(JSON.parse(String(init.body))).toEqual({ reason: REASON });
  });

  it("sunucunun gerekçe hatasını alana bağlar", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(400, {
          code: "VALIDATION_FAILED",
          message: "Gönderilen değerler geçersiz.",
          fieldErrors: [{ field: "reason", message: "Gerekçe en az 10, en çok 500 karakter olmalıdır" }],
        }),
      ),
    );
    renderWithIntl(<SellerDecisionActions sellerId={7} status="APPROVED" />);

    fireEvent.click(screen.getByRole("button", { name: "Askıya al" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.change(screen.getByLabelText("Gerekçe"), { target: { value: REASON } });
    fireEvent.click(submitIn(dialog));

    expect(await screen.findByText("Gerekçe en az 10, en çok 500 karakter olmalıdır")).toBeDefined();
    expect(router.refresh).not.toHaveBeenCalled();
  });

  it("geçiş tablosuna göre yalnızca yapılabilen eylemi gösterir", () => {
    const { unmount } = renderWithIntl(<SellerDecisionActions sellerId={7} status="APPROVED" />);
    expect(screen.getByRole("button", { name: "Askıya al" })).toBeDefined();
    expect(screen.queryByRole("button", { name: "Onayla" })).toBeNull();
    unmount();

    const suspended = renderWithIntl(<SellerDecisionActions sellerId={7} status="SUSPENDED" />);
    expect(screen.getByRole("button", { name: "Askıdan çıkar" })).toBeDefined();
    suspended.unmount();

    const { container } = renderWithIntl(<SellerDecisionActions sellerId={7} status="REJECTED" />);
    expect(container.textContent).toBe("");
  });
});
