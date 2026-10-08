"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { MapPinHouse, Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { FormMessage } from "@/features/account/components/FormMessage";
import { toFormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import type { AddressView, DeliveryProvince } from "@/features/addresses/api";
import { addressBffPaths } from "@/features/addresses/bff";
import { postToBff } from "@/lib/bffClient";
import { AddressForm } from "./AddressForm";

/** Hesap başına adres sınırı; `api` ile aynı (D5). Sınırda "ekle" eylemi gizlenir. */
const MAX_ADDRESSES = 10;

/**
 * Adres defteri (cart-and-checkout spec'i, D5): liste, ekleme, düzenleme ve
 * iki adımlı silme (yıkıcı eylem onaylı, Fitts). Teslimat listesinden çıkmış
 * ildeki adres işaretlenir.
 */
export function AddressBook({
  addresses,
  provinces,
}: {
  readonly addresses: readonly AddressView[];
  readonly provinces: readonly DeliveryProvince[];
}) {
  const t = useTranslations("addresses");
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const [editing, setEditing] = useState<number | "new" | undefined>(addresses.length === 0 ? "new" : undefined);
  const [confirmingDelete, setConfirmingDelete] = useState<number | undefined>(undefined);
  const [error, setError] = useState<string | undefined>(undefined);
  const [pending, setPending] = useState(false);

  async function remove(id: number) {
    setPending(true);
    setError(undefined);
    try {
      await postToBff(addressBffPaths.address(id), undefined, "DELETE");
      setConfirmingDelete(undefined);
      router.refresh();
    } catch (cause) {
      setError(toFormErrors(cause, errorTexts).general);
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {error !== undefined && <FormMessage tone="error">{error}</FormMessage>}
      {addresses.length === 0 && editing !== "new" && (
        <p className="text-muted-foreground">{t("empty")}</p>
      )}
      <ul className="flex flex-col gap-3">
        {addresses.map((address) => (
          <li key={address.id}>
            <Card>
              <CardContent className="flex flex-col gap-3">
                {editing === address.id ? (
                  <AddressForm
                    provinces={provinces}
                    address={address}
                    onSaved={() => setEditing(undefined)}
                    onCancel={() => setEditing(undefined)}
                  />
                ) : (
                  <>
                    <div className="flex flex-wrap items-center gap-2">
                      <MapPinHouse aria-hidden className="size-4 text-muted-foreground" />
                      <h2 className="font-semibold">{address.title}</h2>
                      {!address.deliverable && <Badge variant="warning">{t("notDeliverable")}</Badge>}
                    </div>
                    <p className="text-sm break-words">
                      {address.recipientName} · {address.phone}
                      <br />
                      {address.addressLine}
                      <br />
                      {address.district} / {address.provinceName}
                      {address.postalCode ? ` ${address.postalCode}` : ""}
                    </p>
                    <div className="flex flex-wrap gap-2">
                      <Button type="button" variant="outline" onClick={() => setEditing(address.id)}>
                        {t("edit")}
                      </Button>
                      {confirmingDelete === address.id ? (
                        <>
                          <Button type="button" variant="destructive" disabled={pending} onClick={() => remove(address.id)}>
                            {t("confirmDelete")}
                          </Button>
                          <Button type="button" variant="ghost" disabled={pending} onClick={() => setConfirmingDelete(undefined)}>
                            {t("cancelDelete")}
                          </Button>
                        </>
                      ) : (
                        <Button type="button" variant="ghost" onClick={() => setConfirmingDelete(address.id)}>
                          {t("delete")}
                        </Button>
                      )}
                    </div>
                  </>
                )}
              </CardContent>
            </Card>
          </li>
        ))}
      </ul>
      {editing === "new" ? (
        <Card>
          <CardContent className="flex flex-col gap-4">
            <h2 className="text-lg font-semibold tracking-tight">{t("newTitle")}</h2>
            <AddressForm
              provinces={provinces}
              onSaved={() => setEditing(undefined)}
              onCancel={addresses.length === 0 ? undefined : () => setEditing(undefined)}
            />
          </CardContent>
        </Card>
      ) : (
        addresses.length < MAX_ADDRESSES && (
          <Button type="button" variant="outline" className="self-start" onClick={() => setEditing("new")}>
            <Plus aria-hidden />
            {t("add")}
          </Button>
        )
      )}
    </div>
  );
}
