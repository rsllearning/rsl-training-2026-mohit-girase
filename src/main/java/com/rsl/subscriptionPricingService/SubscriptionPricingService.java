package com.rsl.subscriptionPricingService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Service for calculating monthly subscription pricing with longevity discounts,
 * promotional voucher codes, floor clamping, and rounding rules.
 */
public class SubscriptionPricingService {

    // Promotional Voucher Codes
    public static final String VOUCHER_SAVE20 = "SAVE20";
    public static final String VOUCHER_HALFPRICE = "HALFPRICE";

    // Rate Constants & Multipliers
    private static final BigDecimal FLOOR_PRICE = new BigDecimal("0.00");
    private static final BigDecimal SAVE20_DEDUCTION = new BigDecimal("20.00");
    private static final BigDecimal HALFPRICE_MULTIPLIER = new BigDecimal("0.50");
    private static final BigDecimal LONGEVITY_10_PERCENT = new BigDecimal("0.90");
    private static final BigDecimal LONGEVITY_25_PERCENT = new BigDecimal("0.75");

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Calculates the net monthly rate based on tier, account longevity, and promotional voucher.
     *
     * @param tier             subscription tier (must not be null)
     * @param accountAgeMonths age of customer account in months (must be >= 0)
     * @param voucherCode      optional promotional voucher code (null or empty for none)
     * @return final calculated monthly rate with 2-decimal half-up precision and $0.00 floor
     */
    public BigDecimal calculateMonthlyRate(SubscriptionTier tier, int accountAgeMonths, String voucherCode) {
        Objects.requireNonNull(tier, "Subscription tier must not be null");
        if (accountAgeMonths < 0) {
            throw new IllegalArgumentException("Account age cannot be negative: " + accountAgeMonths);
        }

        BigDecimal rateAfterLongevity = applyLongevityDiscount(tier.getBaseRate(), accountAgeMonths);
        BigDecimal rateAfterVoucher = applyVoucherDiscount(rateAfterLongevity, voucherCode);
        return clampToFloorAndRound(rateAfterVoucher);
    }

    /**
     * Applies longevity percentage discount based on account age.
     * Longevity discount: 25% if > 36 months, 10% if > 12 months, 0% otherwise.
     */
    private BigDecimal applyLongevityDiscount(BigDecimal baseRate, int accountAgeMonths) {
        if (accountAgeMonths > 36) {
            return baseRate.multiply(LONGEVITY_25_PERCENT);
        }
        if (accountAgeMonths > 12) {
            return baseRate.multiply(LONGEVITY_10_PERCENT);
        }
        return baseRate;
    }

    /**
     * Applies promotional voucher deductions using modern Java switch expression.
     */
    private BigDecimal applyVoucherDiscount(BigDecimal rate, String voucherCode) {
        if (voucherCode == null || voucherCode.isEmpty()) {
            return rate;
        }

        return switch (voucherCode) {
            case VOUCHER_SAVE20 -> rate.subtract(SAVE20_DEDUCTION);
            case VOUCHER_HALFPRICE -> rate.multiply(HALFPRICE_MULTIPLIER);
            default -> throw new InvalidVoucherException("Invalid or unrecognized voucher code: " + voucherCode);
        };
    }

    /**
     * Rounds monetary rate to 2 decimal places using HALF_UP rounding and clamps to $0.00 minimum floor.
     */
    private BigDecimal clampToFloorAndRound(BigDecimal rate) {
        BigDecimal rounded = rate.setScale(SCALE, ROUNDING_MODE);
        return rounded.max(FLOOR_PRICE);
    }
}
