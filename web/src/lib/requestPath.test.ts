import { describe, expect, it } from "vitest";
import { requestPathOf, returnPathFor } from "./requestPath";

/**
 * Oturum kapısının dönüş adresi (ADR-102): sorgu korunur, iç parametre atılır,
 * korunan yolun dışına ve siteden çıkılamaz.
 */

describe("requestPathOf", () => {
  it("yolu ve sorguyu birlikte verir", () => {
    expect(requestPathOf(new URL("http://localhost:3000/admin/claims?status=CLOSED&page=2"))).toBe(
      "/admin/claims?status=CLOSED&page=2",
    );
  });

  it("sorgu yoksa yalnızca yolu verir", () => {
    expect(requestPathOf(new URL("http://localhost:3000/account"))).toBe("/account");
  });

  it("Next.js'in iç _rsc parametresini atar", () => {
    expect(requestPathOf(new URL("http://localhost:3000/seller/orders?status=NEW&_rsc=abc"))).toBe(
      "/seller/orders?status=NEW",
    );
    expect(requestPathOf(new URL("http://localhost:3000/seller/orders?_rsc=abc"))).toBe("/seller/orders");
  });
});

describe("returnPathFor", () => {
  it("korunan yolun sorgusunu korur", () => {
    expect(returnPathFor("/admin/claims", "/admin/claims?status=CLOSED")).toBe("/admin/claims?status=CLOSED");
  });

  it("korunan yolun alt sayfasına döner", () => {
    expect(returnPathFor("/account/orders", "/account/orders/BHC-ORNEK0001")).toBe("/account/orders/BHC-ORNEK0001");
  });

  it("başlık yoksa korunan yolu kullanır", () => {
    expect(returnPathFor("/admin/claims", null)).toBe("/admin/claims");
  });

  it.each(["https://saldirgan.test/admin/claims", "//saldirgan.test", "/admin/claimsx", "/seller/orders"])(
    "%s korunan yolun altında değilse korunan yolu kullanır",
    (requested) => {
      expect(returnPathFor("/admin/claims", requested)).toBe("/admin/claims");
    },
  );
});
