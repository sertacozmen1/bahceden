package com.bahceden.order.domain;

/** Satıcı siparişini kargodan önce iptal eden taraf (ADR-056 §1). */
public enum CancelledBy {
    BUYER,
    SELLER
}
