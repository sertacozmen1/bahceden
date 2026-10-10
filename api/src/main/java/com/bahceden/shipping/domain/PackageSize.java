package com.bahceden.shipping.domain;

/**
 * Satıcının seçtiği koli boyutu (add-shipments D3).
 *
 * <p>Sahada telefonla çalışan satıcıya serbest desi yerine üç seçenek sunulur;
 * desi karşılığı yapılandırmadadır ve yalnızca entegratöre bildirilir, kargo
 * ücretini etkilemez (ADR-058).
 */
public enum PackageSize {
    SMALL,
    MEDIUM,
    LARGE
}
