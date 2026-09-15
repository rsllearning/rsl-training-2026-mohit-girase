package com.rsl.subscriptionPricingService;

public class InvalidVoucherException extends RuntimeException {

    public InvalidVoucherException(String message) {
        super(message);
    }
}
