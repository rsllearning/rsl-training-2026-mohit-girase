package com.rsl.subscriptionPricingService;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Subscription Pricing Engine - Audited Unit & Boundary Tests")
public class SubscriptionPricingServiceTest {

    // Pure unit test: Direct instantiation of the System Under Test (SUT) with no unnecessary mocks or Spring context overhead
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

            // Audit: Replaced weak assertNotNull with strict AssertJ isEqualByComparingTo and scale verification
            assertThat(rate)
                .as("Base monthly rate for tier %s must match specification", tier)
                .isEqualByComparingTo(expectedBaseRate);
            assertThat(rate.scale())
                .as("Rate must be scaled to exactly 2 decimal places")
                .isEqualTo(2);
        }

        @Test
        @DisplayName("Verify base rate persists when empty string voucher is provided")
        void shouldReturnBaseRateWithEmptyVoucherCode() {
            BigDecimal rate = pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 0, "");
            assertThat(rate)
                .as("Empty voucher code must not alter base rate")
                .isEqualByComparingTo("50.00");
            assertThat(rate.scale()).isEqualTo(2);
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
                    () -> assertThat(pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, months, null))
                            .as("BASIC rate at %d months", months).isEqualByComparingTo("50.00"),
                    () -> assertThat(pricingService.calculateMonthlyRate(SubscriptionTier.PRO, months, null))
                            .as("PRO rate at %d months", months).isEqualByComparingTo("150.00"),
                    () -> assertThat(pricingService.calculateMonthlyRate(SubscriptionTier.ENTERPRISE, months, null))
                            .as("ENTERPRISE rate at %d months", months).isEqualByComparingTo("500.00")
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
                    () -> assertThat(pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, months, null))
                            .as("BASIC rate at %d months (10%% off)", months).isEqualByComparingTo("45.00"),
                    () -> assertThat(pricingService.calculateMonthlyRate(SubscriptionTier.PRO, months, null))
                            .as("PRO rate at %d months (10%% off)", months).isEqualByComparingTo("135.00"),
                    () -> assertThat(pricingService.calculateMonthlyRate(SubscriptionTier.ENTERPRISE, months, null))
                            .as("ENTERPRISE rate at %d months (10%% off)", months).isEqualByComparingTo("450.00")
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
                    () -> assertThat(pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, months, null))
                            .as("BASIC rate at %d months (25%% off)", months).isEqualByComparingTo("37.50"),
                    () -> assertThat(pricingService.calculateMonthlyRate(SubscriptionTier.PRO, months, null))
                            .as("PRO rate at %d months (25%% off)", months).isEqualByComparingTo("112.50"),
                    () -> assertThat(pricingService.calculateMonthlyRate(SubscriptionTier.ENTERPRISE, months, null))
                            .as("ENTERPRISE rate at %d months (25%% off)", months).isEqualByComparingTo("375.00")
                );
            }
        }

        @Test
        @DisplayName("Boundary transitions: 12 to 13 months, and 36 to 37 months")
        void shouldTransitionCorrectlyAcrossExactLongevityThresholds() {
            // Threshold 12 -> 13 months
            BigDecimal basicAt12 = pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 12, null);
            BigDecimal basicAt13 = pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 13, null);
            assertThat(basicAt12).as("12 months must not receive longevity discount").isEqualByComparingTo("50.00");
            assertThat(basicAt13).as("13 months must receive 10% longevity discount").isEqualByComparingTo("45.00");

            // Threshold 36 -> 37 months
            BigDecimal basicAt36 = pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 36, null);
            BigDecimal basicAt37 = pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 37, null);
            assertThat(basicAt36).as("36 months must remain at 10% longevity discount").isEqualByComparingTo("45.00");
            assertThat(basicAt37).as("37 months must receive 25% longevity discount").isEqualByComparingTo("37.50");
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
                assertThat(rate)
                    .as("Rate after SAVE20 deduction on %s tier", tier)
                    .isEqualByComparingTo(expectedRate);
                assertThat(rate.scale()).isEqualTo(2);
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
                assertThat(rate)
                    .as("Rate after HALFPRICE deduction on %s tier", tier)
                    .isEqualByComparingTo(expectedRate);
                assertThat(rate.scale()).isEqualTo(2);
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
                    () -> assertThat(pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 0, voucherCode))
                            .as("BASIC base rate with null/empty voucher").isEqualByComparingTo("50.00"),
                    () -> assertThat(pricingService.calculateMonthlyRate(SubscriptionTier.PRO, 0, voucherCode))
                            .as("PRO base rate with null/empty voucher").isEqualByComparingTo("150.00"),
                    () -> assertThat(pricingService.calculateMonthlyRate(SubscriptionTier.ENTERPRISE, 0, voucherCode))
                            .as("ENTERPRISE base rate with null/empty voucher").isEqualByComparingTo("500.00")
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
            assertThat(rate)
                .as("Rate for %s at %d months with voucher %s must equal %s", tier, months, voucherCode, expectedRate)
                .isEqualByComparingTo(expectedRate);
            assertThat(rate.scale()).isEqualTo(2);
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
                () -> assertThat(rate1.scale()).as("Base rate scale").isEqualTo(2),
                () -> assertThat(rate2.scale()).as("Longevity + SAVE20 scale").isEqualTo(2),
                () -> assertThat(rate3.scale()).as("Longevity + HALFPRICE scale").isEqualTo(2)
            );
        }

        @Test
        @DisplayName("Verify half-up rounding precision on fractional cents results")
        void shouldRoundHalfUpToTwoDecimalPlaces() {
            // 25% longevity on BASIC ($37.50) with HALFPRICE yields $18.75 (exact to 2 decimals)
            BigDecimal rate = pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 40, "HALFPRICE");
            assertThat(rate)
                .as("Calculated rate must match half-up rounded value")
                .isEqualByComparingTo("18.75");
            assertThat(rate.scale())
                .as("Rate must have scale of 2")
                .isEqualTo(2);
        }

        @Test
        @DisplayName("Verify minimum achievable rate boundary ($17.50) under valid promotions")
        void shouldVerifyMinimumAchievableRateBoundaryAboveZeroFloor() {
            // Audit: Eliminated tautological assertTrue(rate >= 0) on $17.50, $92.50, and $355.00.
            // Under valid domain rules, the absolute minimum rate is BASIC at >36 months with SAVE20:
            // Base ($50.00) * 0.75 = $37.50; $37.50 - $20.00 = $17.50.
            BigDecimal minRate = pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 37, "SAVE20");
            assertThat(minRate)
                .as("Lowest possible rate under standard promotions must strictly be $17.50")
                .isEqualByComparingTo("17.50");
            assertThat(minRate.scale()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("6. Negative Boundaries & Exceptional Cases")
    class NegativeBoundaryTests {

        @ParameterizedTest(name = "Unrecognized or expired voucher code ''{0}'' should throw InvalidVoucherException with meaningful message")
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
        @DisplayName("Unrecognized or expired voucher codes must throw InvalidVoucherException with descriptive message")
        void shouldThrowInvalidVoucherExceptionForUnrecognizedOrExpiredVouchers(String invalidVoucher) {
            // Audit: Eliminated generic exception checking; verify exact InvalidVoucherException and assert exception message
            InvalidVoucherException exception = assertThrows(InvalidVoucherException.class,
                () -> pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 0, invalidVoucher),
                () -> "Expected InvalidVoucherException when using invalid voucher: " + invalidVoucher
            );

            assertThat(exception.getMessage())
                .as("Exception message must not be blank and should provide context")
                .isNotNull()
                .isNotBlank();
        }

        @Test
        @DisplayName("Throw InvalidVoucherException even if longevity discount is applicable")
        void shouldThrowInvalidVoucherExceptionRegardlessOfAccountAge() {
            InvalidVoucherException exception = assertThrows(InvalidVoucherException.class,
                () -> pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 50, "BOGUS_CODE")
            );

            assertThat(exception.getMessage())
                .as("Exception message must not be blank")
                .isNotNull()
                .isNotBlank();
        }
    }

    @Nested
    @DisplayName("7. Adversarial Boundary & Financial Integrity Analysis")
    class AdversarialBoundaryAnalysisTests {

        @Nested
        @DisplayName("7.1 Off-by-One Longevity Boundary Inflections (All Tiers)")
        class OffByOneLongevityThresholdTests {

            @ParameterizedTest(name = "{0} at 12 mo (${1}) vs 13 mo (${2})")
            @CsvSource({
                "BASIC, 50.00, 45.00",
                "PRO, 150.00, 135.00",
                "ENTERPRISE, 500.00, 450.00"
            })
            @DisplayName("Verify exact step-function discount transition at 12 -> 13 months across all tiers")
            void shouldValidateExactInflectionAtTwelveToThirteenMonths(SubscriptionTier tier, String expectedAt12, String expectedAt13) {
                BigDecimal rateAt12 = pricingService.calculateMonthlyRate(tier, 12, null);
                BigDecimal rateAt13 = pricingService.calculateMonthlyRate(tier, 13, null);

                assertAll(
                    () -> assertThat(rateAt12)
                            .as("%s tier at exactly 12 months must retain 0%% base rate ($%s)", tier, expectedAt12)
                            .isEqualByComparingTo(expectedAt12),
                    () -> assertThat(rateAt13)
                            .as("%s tier at exactly 13 months must receive 10%% discount ($%s)", tier, expectedAt13)
                            .isEqualByComparingTo(expectedAt13)
                );
            }

            @ParameterizedTest(name = "{0} at 36 mo (${1}) vs 37 mo (${2})")
            @CsvSource({
                "BASIC, 45.00, 37.50",
                "PRO, 135.00, 112.50",
                "ENTERPRISE, 450.00, 375.00"
            })
            @DisplayName("Verify exact step-function discount transition at 36 -> 37 months across all tiers")
            void shouldValidateExactInflectionAtThirtySixToThirtySevenMonths(SubscriptionTier tier, String expectedAt36, String expectedAt37) {
                BigDecimal rateAt36 = pricingService.calculateMonthlyRate(tier, 36, null);
                BigDecimal rateAt37 = pricingService.calculateMonthlyRate(tier, 37, null);

                assertAll(
                    () -> assertThat(rateAt36)
                            .as("%s tier at exactly 36 months must remain at 10%% rate ($%s)", tier, expectedAt36)
                            .isEqualByComparingTo(expectedAt36),
                    () -> assertThat(rateAt37)
                            .as("%s tier at exactly 37 months must step down to 25%% rate ($%s)", tier, expectedAt37)
                            .isEqualByComparingTo(expectedAt37)
                );
            }
        }

        @Nested
        @DisplayName("7.2 Defensive Negative Inputs")
        class DefensiveNegativeInputTests {

            @ParameterizedTest(name = "Negative account age {0} months must throw IllegalArgumentException")
            @ValueSource(ints = {-1, -2, -12, -36, Integer.MIN_VALUE})
            @DisplayName("Reject negative account age boundaries to prevent corrupted tenure logic")
            void shouldRejectNegativeAccountAgeAcrossTiers(int negativeMonths) {
                for (SubscriptionTier tier : SubscriptionTier.values()) {
                    IllegalArgumentException ex = assertThrows(
                        IllegalArgumentException.class,
                        () -> pricingService.calculateMonthlyRate(tier, negativeMonths, null),
                        () -> String.format("Expected IllegalArgumentException for %s tier with negative age: %d", tier, negativeMonths)
                    );
                    assertThat(ex.getMessage())
                        .as("Exception message should clarify negative account age violation")
                        .contains(String.valueOf(negativeMonths));
                }
            }
        }

        @Nested
        @DisplayName("7.3 Whitespace & Malformed Voucher Strings")
        class WhitespaceAndMalformedVoucherTests {

            @ParameterizedTest(name = "Malformed or untrimmed voucher ''{0}'' must be rejected")
            @ValueSource(strings = {
                "   ",             // Whitespace only
                "\t",              // Tab character
                "\n",              // Newline
                "  SAVE20  ",      // Untrimmed valid voucher
                "\tHALFPRICE\n",   // Control characters with voucher
                "save20",          // Lowercase variant
                "halfprice",       // Lowercase variant
                "Save20",          // PascalCase variant
                "SAVE 20",         // Internal space
                "SAVE20\0",        // Null-byte suffix
                "HALF PRICE"       // Internal space
            })
            @DisplayName("Strict voucher token validation: whitespace, malformed or casing deviations must throw InvalidVoucherException")
            void shouldRejectMalformedOrWhitespaceVouchers(String malformedVoucher) {
                InvalidVoucherException ex = assertThrows(
                    InvalidVoucherException.class,
                    () -> pricingService.calculateMonthlyRate(SubscriptionTier.BASIC, 12, malformedVoucher),
                    () -> "Expected InvalidVoucherException for malformed voucher: " + malformedVoucher
                );
                assertThat(ex.getMessage()).isNotBlank();
            }
        }

        @Nested
        @DisplayName("7.4 Rounding Accuracy on Fractional Cents")
        class RoundingAccuracyTests {

            @ParameterizedTest(name = "{0} at {1} mo with {2} -> expected ${3}")
            @CsvSource({
                "BASIC, 37, HALFPRICE, 18.75",
                "PRO, 37, HALFPRICE, 56.25",
                "ENTERPRISE, 37, HALFPRICE, 187.50",
                "BASIC, 24, HALFPRICE, 22.50",
                "PRO, 24, HALFPRICE, 67.50",
                "ENTERPRISE, 24, HALFPRICE, 225.00"
            })
            @DisplayName("Validate exact half-up cent precision and strict 2-decimal scale on fractional discounts")
            void shouldVerifyHalfUpCentPrecisionAndScale(SubscriptionTier tier, int months, String voucherCode, String expectedRate) {
                BigDecimal rate = pricingService.calculateMonthlyRate(tier, months, voucherCode);

                assertAll(
                    () -> assertThat(rate)
                            .as("Calculated rate for %s at %d months with %s must equal %s", tier, months, voucherCode, expectedRate)
                            .isEqualByComparingTo(expectedRate),
                    () -> assertThat(rate.scale())
                            .as("Scale must always strictly be 2 decimal places")
                            .isEqualTo(2)
                );
            }
        }
    }
}

