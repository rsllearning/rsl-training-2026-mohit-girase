package com.rsl.subscriptionPricingService;

import java.math.BigDecimal;

public enum SubscriptionTier {
    BASIC(new BigDecimal("50.00")),
    PRO(new BigDecimal("150.00")),
    ENTERPRISE(new BigDecimal("500.00"));

    private final BigDecimal baseRate;

    SubscriptionTier(BigDecimal baseRate) {
        this.baseRate = baseRate;
    }

    public BigDecimal getBaseRate() {
        return baseRate;
    }
}
