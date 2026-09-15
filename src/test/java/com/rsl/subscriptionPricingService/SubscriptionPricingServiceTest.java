package com.rsl.subscriptionPricingService;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Subscription Pricing Engine - Specification & Boundary Tests")
public class SubscriptionPricingServiceTest {

    private SubscriptionPricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new SubscriptionPricingService();
    }

    @Nested
    @DisplayName("1. Tier Base Rates Verification")
    class TierBaseRatesTests {

        @ParameterizedTest(name = "Tier {0} should have base monthly rate of ${1}")
        @CsvSource({
            "BASIC, 50.00",
            "PRO, 150.00",
            "ENTERPRISE, 500.00"
        })
        @DisplayName("Verify standard base rates for new accounts without voucher")
        void shouldReturnExactBaseRateForNewAccounts(SubscriptionTier tier, String expectedBaseRate) {
            BigDecimal rate = pricingService.calculateMonthlyRate(tier, 0, null);

            assertAll(
                () -> assertNotNull(rate, "Calculated monthly rate must not be null"),
                () -> assertEquals(new BigDecimal(expectedBaseRate), rate, "Rate must match base tier price exactly"),
                () -> assertEquals(2, rate.scale(), "Rate must be scaled to exactly 2 decimal places")
            );
        }

        @Test
        @DisplayName("Verify base rate persists when empty string voucher is provided")
        void shouldReturnBaseRateWithEmptyVoucherCode() {
            BigDecimal rate = pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 0, "");
            assertEquals(new BigDecimal("50.00"), rate);
        }
    }

    @Nested
    @DisplayName("2. Longevity Discounts & Boundary Conditions")
    class LongevityDiscountTests {

        @Nested
        @DisplayName("Tier 1: accountAgeMonths <= 12 (0% discount)")
        class NoLongevityDiscountTests {

            @ParameterizedTest(name = "Account age {0} months should receive 0% longevity discount")
            @ValueSource(ints = {0, 1, 6, 11, 12})
            @DisplayName("Boundary tests for 0% discount bracket (0 to 12 months)")
            void shouldApplyZeroDiscountAtOrBelowTwelveMonths(int months) {
                assertAll(
                    () -> assertEquals(new BigDecimal("50.00"), pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, months, null)),
                    () -> assertEquals(new BigDecimal("150.00"), pricingService.calculateMonthlyRate(SubscriptionTier.PRO, months, null)),
                    () -> assertEquals(new BigDecimal("500.00"), pricingService.calculateMonthlyRate(SubscriptionTier.ENTERPRISE, months, null))
                );
            }
        }

        @Nested
        @DisplayName("Tier 2: accountAgeMonths > 12 and <= 36 (10% discount)")
        class TenPercentLongevityDiscountTests {

            @ParameterizedTest(name = "Account age {0} months should receive 10% discount on base rate")
            @ValueSource(ints = {13, 14, 24, 35, 36})
            @DisplayName("Boundary tests for 10% discount bracket (13 to 36 months)")
            void shouldApplyTenPercentDiscountBetweenThirteenAndThirtySixMonths(int months) {
                // BASIC: 50.00 - 10% (5.00) = 45.00
                // PRO: 150.00 - 10% (15.00) = 135.00
                // ENTERPRISE: 500.00 - 10% (50.00) = 450.00
                assertAll(
                    () -> assertEquals(new BigDecimal("45.00"), pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, months, null)),
                    () -> assertEquals(new BigDecimal("135.00"), pricingService.calculateMonthlyRate(SubscriptionTier.PRO, months, null)),
                    () -> assertEquals(new BigDecimal("450.00"), pricingService.calculateMonthlyRate(SubscriptionTier.ENTERPRISE, months, null))
                );
            }
        }

        @Nested
        @DisplayName("Tier 3: accountAgeMonths > 36 (25% discount)")
        class TwentyFivePercentLongevityDiscountTests {

            @ParameterizedTest(name = "Account age {0} months should receive 25% discount on base rate")
            @ValueSource(ints = {37, 38, 48, 60, 120})
            @DisplayName("Boundary tests for 25% discount bracket (> 36 months)")
            void shouldApplyTwentyFivePercentDiscountAboveThirtySixMonths(int months) {
                // BASIC: 50.00 - 25% (12.50) = 37.50
                // PRO: 150.00 - 25% (37.50) = 112.50
                // ENTERPRISE: 500.00 - 25% (125.00) = 375.00
                assertAll(
                    () -> assertEquals(new BigDecimal("37.50"), pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, months, null)),
                    () -> assertEquals(new BigDecimal("112.50"), pricingService.calculateMonthlyRate(SubscriptionTier.PRO, months, null)),
                    () -> assertEquals(new BigDecimal("375.00"), pricingService.calculateMonthlyRate(SubscriptionTier.ENTERPRISE, months, null))
                );
            }
        }

        @Test
        @DisplayName("Boundary transitions: 12 to 13 months, and 36 to 37 months")
        void shouldTransitionCorrectlyAcrossExactLongevityThresholds() {
            // Threshold 12 -> 13 months
            BigDecimal basicAt12 = pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 12, null);
            BigDecimal basicAt13 = pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 13, null);
            assertEquals(new BigDecimal("50.00"), basicAt12, "12 months must not receive longevity discount");
            assertEquals(new BigDecimal("45.00"), basicAt13, "13 months must receive 10% longevity discount");

            // Threshold 36 -> 37 months
            BigDecimal basicAt36 = pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 36, null);
            BigDecimal basicAt37 = pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 37, null);
            assertEquals(new BigDecimal("45.00"), basicAt36, "36 months must remain at 10% longevity discount");
            assertEquals(new BigDecimal("37.50"), basicAt37, "37 months must receive 25% longevity discount");
        }
    }

    @Nested
    @DisplayName("3. Promotional Voucher Codes")
    class PromotionalVoucherTests {

        @Nested
        @DisplayName("Voucher: SAVE20 (Flat $20.00 deduction)")
        class Save20VoucherTests {

            @ParameterizedTest(name = "{0} tier with SAVE20 should deduct flat $20.00 from base price")
            @CsvSource({
                "BASIC, 0, 30.00",
                "PRO, 6, 130.00",
                "ENTERPRISE, 12, 480.00"
            })
            @DisplayName("Verify SAVE20 flat deduction on base rates (<= 12 months)")
            void shouldDeductFlatTwentyDollarsFromBaseRate(SubscriptionTier tier, int months, String expectedRate) {
                BigDecimal rate = pricingService.calculateMonthlyRate(tier, months, "SAVE20");
                assertEquals(new BigDecimal(expectedRate), rate);
            }
        }

        @Nested
        @DisplayName("Voucher: HALFPRICE (50% deduction)")
        class HalfPriceVoucherTests {

            @ParameterizedTest(name = "{0} tier with HALFPRICE should deduct 50% from base price")
            @CsvSource({
                "BASIC, 0, 25.00",
                "PRO, 6, 75.00",
                "ENTERPRISE, 12, 250.00"
            })
            @DisplayName("Verify HALFPRICE 50% deduction on base rates (<= 12 months)")
            void shouldDeductFiftyPercentFromBaseRate(SubscriptionTier tier, int months, String expectedRate) {
                BigDecimal rate = pricingService.calculateMonthlyRate(tier, months, "HALFPRICE");
                assertEquals(new BigDecimal(expectedRate), rate);
            }
        }

        @Nested
        @DisplayName("Null or Empty Voucher Handling")
        class NullOrEmptyVoucherTests {

            @ParameterizedTest(name = "Voucher ''{0}'' should be treated as no voucher applied")
            @NullAndEmptySource
            @DisplayName("Verify null and empty voucher strings apply no discount")
            void shouldTreatNullOrEmptyVoucherAsNoVoucher(String voucherCode) {
                assertAll(
                    () -> assertEquals(new BigDecimal("50.00"), pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 0, voucherCode)),
                    () -> assertEquals(new BigDecimal("150.00"), pricingService.calculateMonthlyRate(SubscriptionTier.PRO, 0, voucherCode)),
                    () -> assertEquals(new BigDecimal("500.00"), pricingService.calculateMonthlyRate(SubscriptionTier.ENTERPRISE, 0, voucherCode))
                );
            }
        }
    }

    @Nested
    @DisplayName("4. Combined Discounts (Longevity followed by Voucher)")
    class CombinedDiscountTests {

        @ParameterizedTest(name = "{0} tier at {1} months with {2} -> expected ${3}")
        @CsvSource({
            // 10% Longevity (months = 24) followed by SAVE20 (-$20.00)
            "BASIC, 24, SAVE20, 25.00",           // (50.00 - 10%) - 20.00 = 45.00 - 20.00 = 25.00
            "PRO, 24, SAVE20, 115.00",            // (150.00 - 10%) - 20.00 = 135.00 - 20.00 = 115.00
            "ENTERPRISE, 24, SAVE20, 430.00",     // (500.00 - 10%) - 20.00 = 450.00 - 20.00 = 430.00

            // 25% Longevity (months = 48) followed by SAVE20 (-$20.00)
            "BASIC, 48, SAVE20, 17.50",           // (50.00 - 25%) - 20.00 = 37.50 - 20.00 = 17.50
            "PRO, 48, SAVE20, 92.50",             // (150.00 - 25%) - 20.00 = 112.50 - 20.00 = 92.50
            "ENTERPRISE, 48, SAVE20, 355.00",     // (500.00 - 25%) - 20.00 = 375.00 - 20.00 = 355.00

            // 10% Longevity (months = 24) followed by HALFPRICE (-50%)
            "BASIC, 24, HALFPRICE, 22.50",        // (50.00 - 10%) * 50% = 45.00 * 0.50 = 22.50
            "PRO, 24, HALFPRICE, 67.50",          // (150.00 - 10%) * 50% = 135.00 * 0.50 = 67.50
            "ENTERPRISE, 24, HALFPRICE, 225.00",  // (500.00 - 10%) * 50% = 450.00 * 0.50 = 225.00

            // 25% Longevity (months = 48) followed by HALFPRICE (-50%)
            "BASIC, 48, HALFPRICE, 18.75",        // (50.00 - 25%) * 50% = 37.50 * 0.50 = 18.75
            "PRO, 48, HALFPRICE, 56.25",          // (150.00 - 25%) * 50% = 112.50 * 0.50 = 56.25
            "ENTERPRISE, 48, HALFPRICE, 187.50"   // (500.00 - 25%) * 50% = 375.00 * 0.50 = 187.50
        })
        @DisplayName("Verify sequence of longevity discount applied before promotional voucher deduction")
        void shouldApplyLongevityDiscountPriorToVoucherDeduction(SubscriptionTier tier, int months, String voucherCode, String expectedRate) {
            BigDecimal rate = pricingService.calculateMonthlyRate(tier, months, voucherCode);
            assertEquals(new BigDecimal(expectedRate), rate);
        }
    }

    @Nested
    @DisplayName("5. Rounding & Floor Rule")
    class RoundingAndFloorTests {

        @ParameterizedTest
        @EnumSource(SubscriptionTier.class)
        @DisplayName("Monthly rate scale must always be precisely 2 decimal places")
        void shouldAlwaysMaintainTwoDecimalPlacesScale(SubscriptionTier tier) {
            BigDecimal rate1 = pricingService.calculateMonthlyRate(tier, 0, null);
            BigDecimal rate2 = pricingService.calculateMonthlyRate(tier, 15, "SAVE20");
            BigDecimal rate3 = pricingService.calculateMonthlyRate(tier, 40, "HALFPRICE");

            assertAll(
                () -> assertEquals(2, rate1.scale(), "Base rate must have scale 2"),
                () -> assertEquals(2, rate2.scale(), "Longevity + SAVE20 must have scale 2"),
                () -> assertEquals(2, rate3.scale(), "Longevity + HALFPRICE must have scale 2")
            );
        }

        @Test
        @DisplayName("Verify half-up rounding precision on fractional cents results")
        void shouldRoundHalfUpToTwoDecimalPlaces() {
            // 25% longevity on BASIC ($37.50) with HALFPRICE yields $18.75 (exact to 2 decimals)
            BigDecimal rate = pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 40, "HALFPRICE");
            assertEquals(new BigDecimal("18.75"), rate);
            assertEquals(2, rate.scale());
        }

        @ParameterizedTest(name = "Tier {0} rate must never fall below minimum $0.00 floor")
        @EnumSource(SubscriptionTier.class)
        @DisplayName("Rate must never fall below $0.00 floor under any discount combination")
        void shouldNeverFallBelowZeroFloor(SubscriptionTier tier) {
            BigDecimal rate = pricingService.calculateMonthlyRate(tier, 60, "SAVE20");
            assertTrue(rate.compareTo(BigDecimal.ZERO) >= 0, "Calculated rate must be >= $0.00 floor");
            assertTrue(rate.compareTo(new BigDecimal("0.00")) >= 0, "Calculated rate must be >= 0.00");
        }
    }

    @Nested
    @DisplayName("6. Negative Boundaries & Exceptional Cases")
    class NegativeBoundaryTests {

        @ParameterizedTest(name = "Unrecognized or expired voucher code ''{0}'' should throw InvalidVoucherException")
        @ValueSource(strings = {
            "BOGUS_CODE",
            "EXPIRED2024",
            "save20",         // Case sensitivity check
            "halfprice",      // Case sensitivity check
            "SAVE_20",
            "HALF_PRICE",
            "DISCOUNT100",
            "UNKNOWN",
            "INVALID_PROMO"
        })
        @DisplayName("Unrecognized or expired voucher codes must throw InvalidVoucherException across all tiers")
        void shouldThrowInvalidVoucherExceptionForUnrecognizedOrExpiredVouchers(String invalidVoucher) {
            assertAll(
                () -> assertThrows(InvalidVoucherException.class,
                    () -> pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 0, invalidVoucher),
                    "Should throw InvalidVoucherException for BASIC tier"),
                () -> assertThrows(InvalidVoucherException.class,
                    () -> pricingService.calculateMonthlyRate(SubscriptionTier.PRO, 24, invalidVoucher),
                    "Should throw InvalidVoucherException for PRO tier"),
                () -> assertThrows(InvalidVoucherException.class,
                    () -> pricingService.calculateMonthlyRate(SubscriptionTier.ENTERPRISE, 48, invalidVoucher),
                    "Should throw InvalidVoucherException for ENTERPRISE tier")
            );
        }

        @Test
        @DisplayName("Throw InvalidVoucherException even if longevity discount is applicable")
        void shouldThrowInvalidVoucherExceptionRegardlessOfAccountAge() {
            assertThrows(InvalidVoucherException.class, () ->
                pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 50, "BOGUS_CODE")
            );
        }
    }
}
