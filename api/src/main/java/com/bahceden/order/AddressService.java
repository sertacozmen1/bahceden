package com.bahceden.order;

import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.geo.TurkishProvinces;
import com.bahceden.common.text.Phones;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.common.web.PageResponse;
import com.bahceden.order.domain.Address;
import com.bahceden.order.dto.AddressRequest;
import com.bahceden.order.dto.AddressView;
import com.bahceden.order.dto.DeliveryProvinceView;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teslimat illeri ve hesabın kayıtlı adresleri (cart-and-checkout spec'i,
 * add-cart-and-checkout D5).
 *
 * <p>İl yalnızca yapılandırmadaki teslimat illerinden seçilir. İl sonradan
 * listeden çıkarılırsa adres kayıtlı kalır, {@code deliverable = false} döner
 * ve siparişte kullanılamaz. Sahiplik sorgu seviyesindedir (ADR-007).
 * Adres kişisel veridir; log'a yazılmaz (K10).
 */
@Service
public class AddressService {

    /** Hesap başına adres sınırı (D5). */
    static final int MAX_ADDRESSES = 10;

    private final AddressRepository addresses;
    private final BahcedenProperties.Commerce commerce;
    private final Clock clock;

    AddressService(AddressRepository addresses, BahcedenProperties properties, Clock clock) {
        this.addresses = addresses;
        this.commerce = properties.commerce();
        this.clock = clock;
    }

    /** Teslimat yapılan iller, yapılandırmadaki sırayla. */
    public PageResponse<DeliveryProvinceView> deliveryProvinces() {
        return PageResponse.ofAll(commerce.deliveryProvinces().stream()
                .map(code -> new DeliveryProvinceView(code, TurkishProvinces.nameOf(code).orElseThrow()))
                .toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<AddressView> list(long accountId) {
        return PageResponse.ofAll(addresses.findByAccountIdOrderByIdAsc(accountId).stream().map(this::toView).toList());
    }

    /**
     * @throws ApiException {@code ADDRESS_LIMIT_REACHED} veya alan bazlı {@code VALIDATION_FAILED}
     */
    @Transactional
    public AddressView create(long accountId, AddressRequest request) {
        var phone = checked(request);
        if (addresses.countByAccountId(accountId) >= MAX_ADDRESSES) {
            throw new ApiException(ApiErrorCode.ADDRESS_LIMIT_REACHED, "error.addressLimitReached");
        }
        var now = clock.instant();
        var address = Address.of(accountId, now);
        apply(address, request, phone);
        return toView(addresses.save(address));
    }

    /** @throws ApiException {@code ADDRESS_NOT_FOUND} veya alan bazlı {@code VALIDATION_FAILED} */
    @Transactional
    public AddressView update(long accountId, long addressId, AddressRequest request) {
        var address = own(accountId, addressId);
        apply(address, request, checked(request));
        return toView(address);
    }

    /** Geçmiş siparişler etkilenmez: sipariş adresin kopyasını taşır (D6). */
    @Transactional
    public void delete(long accountId, long addressId) {
        addresses.delete(own(accountId, addressId));
    }

    /** Hesabın adresi; başkasınınki veya olmayan 404. Sipariş oluşturma da kullanır. */
    Address own(long accountId, long addressId) {
        return addresses.findByIdAndAccountId(addressId, accountId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ADDRESS_NOT_FOUND, "error.addressNotFound"));
    }

    boolean isDeliverable(Address address) {
        return commerce.deliveryProvinces().contains(address.getProvinceCode());
    }

    /** İl ve telefon denetimi; normalleştirilmiş telefonu döner. */
    private String checked(AddressRequest request) {
        if (!commerce.deliveryProvinces().contains(request.provinceCode())) {
            throw ApiException.invalidField("provinceCode", "validation.province.notDelivered");
        }
        return Phones.normalizeTurkish(request.phone())
                .orElseThrow(() -> ApiException.invalidField("phone", "validation.phone.format"));
    }

    private void apply(Address address, AddressRequest request, String phone) {
        address.update(request.title().trim(), request.recipientName().trim(), phone, request.provinceCode(),
                request.district().trim(), request.addressLine().trim(),
                Optional.ofNullable(request.postalCode()).filter(value -> !value.isBlank()).orElse(null),
                clock.instant());
    }

    private AddressView toView(Address address) {
        return new AddressView(address.getId(), address.getTitle(), address.getRecipientName(), address.getPhone(),
                address.getProvinceCode(), TurkishProvinces.nameOf(address.getProvinceCode()).orElseThrow(),
                address.getDistrict(), address.getAddressLine(), address.getPostalCode().orElse(null),
                isDeliverable(address));
    }
}
