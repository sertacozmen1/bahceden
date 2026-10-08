import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { AdminProductSummary } from "@/features/admin/api";
import { ProductModerationActions } from "./ProductModerationActions";

/**
 * İlan denetim eylemi (product-listings spec'i "Admin ilanı kaldırır ve yeniden
 * açar"). Ağ ve yönlendirici taklit edilir.
 */

const router = { refresh: vi.fn() };

vi.mock("next/navigation", () => ({ useRouter: () => router }));

const REASON = "Fotoğraflar ilandaki ürünü göstermiyor.";

function aProduct(status: AdminProductSummary["status"]): AdminProductSummary {
  return {
    id: 9,
    slug: "seftali",
    name: "Şeftali",
    status,
    seller: { slug: "uretici", displayName: "Üretici Bahçesi", city: "Manisa" },
    updatedAt: "2026-10-08T09:30:00Z",
  };
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

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

describe("ProductModerationActions", () => {
  it("yayındaki ilanı gerekçeyle kaldırır", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, aProduct("REMOVED")));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<ProductModerationActions product={aProduct("PUBLISHED")} />);

    fireEvent.click(screen.getByRole("button", { name: "Kaldır" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.change(screen.getByLabelText("Gerekçe"), { target: { value: REASON } });
    fireEvent.click(submitIn(dialog));

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/admin/products/9/remove");
    expect(JSON.parse(String(init.body))).toEqual({ reason: REASON });
  });

  it("kısa gerekçeyi göndermez", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<ProductModerationActions product={aProduct("PUBLISHED")} />);

    fireEvent.click(screen.getByRole("button", { name: "Kaldır" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.change(screen.getByLabelText("Gerekçe"), { target: { value: "kısa" } });
    fireEvent.click(submitIn(dialog));

    expect(await screen.findByText("Gerekçe en az 10 karakter olmalıdır.")).toBeDefined();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("kaldırılmış ilanı yeniden açar", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, aProduct("PUBLISHED")));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<ProductModerationActions product={aProduct("REMOVED")} />);

    fireEvent.click(screen.getByRole("button", { name: "Yeniden aç" }));

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/admin/products/9/reinstate");
  });
});
