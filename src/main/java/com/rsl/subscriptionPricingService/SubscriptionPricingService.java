package com.rsl.subscriptionPricingService;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SubscriptionPricingService {

    private static final BigDecimal FLOOR_PRICE = new BigDecimal("0.00");
    private static final BigDecimal SAVE20_DEDUCTION = new BigDecimal("20.00");
    private static final BigDecimal HALFPRICE_MULTIPLIER = new BigDecimal("0.50");
    private static final BigDecimal LONGEVITY_10_PERCENT = new BigDecimal("0.90");
    private static final BigDecimal LONGEVITY_25_PERCENT = new BigDecimal("0.75");

    public BigDecimal calculateMonthlyRate(SubscriptionTier tier, int accountAgeMonths, String voucherCode) {
        if (tier == null) {
            throw new IllegalArgumentException("Subscription tier cannot be null");
        }

        // 1. Base rate per tier
        BigDecimal rate = tier.getBaseRate();

        // 2. Longevity discount: 25% if > 36 months, 10% if > 12 months
        if (accountAgeMonths > 36) {
            rate = rate.multiply(LONGEVITY_25_PERCENT);
        } else if (accountAgeMonths > 12) {
            rate = rate.multiply(LONGEVITY_10_PERCENT);
        }

        // 3. Promotional Voucher
        if (voucherCode != null && !voucherCode.isEmpty()) {
            switch (voucherCode) {
                case "SAVE20":
                    rate = rate.subtract(SAVE20_DEDUCTION);
                    break;
                case "HALFPRICE":
                    rate = rate.multiply(HALFPRICE_MULTIPLIER);
                    break;
                default:
                    throw new InvalidVoucherException("Invalid or unrecognized voucher code: " + voucherCode);
            }
        }

        // 4. Rounding: half-up rounding to 2 decimal places
        rate = rate.setScale(2, RoundingMode.HALF_UP);

        // 5. Floor: Math.max($0.00, rate)
        if (rate.compareTo(FLOOR_PRICE) < 0) {
            rate = FLOOR_PRICE;
        }

        return rate;
    }
}
