package com.sky.utils;

import java.util.UUID;

/** Generates globally unique, payment-provider-compatible order numbers. */
public final class OrderNumberGenerator {

    private OrderNumberGenerator() {
    }

    /** WeChat Pay allows up to 32 characters for out_trade_no. */
    public static String next() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
