package com.contoso.store;

/** A product in the store. The price is in whole US dollars. */
public record Product(String sku, String name, int price, int stock) {
}
