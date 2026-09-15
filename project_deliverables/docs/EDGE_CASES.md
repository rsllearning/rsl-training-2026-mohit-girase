# Edge Cases & Financial Boundary Analysis

## AI Brainstorm List: Identified Edge Cases & Failure Modes

1. **Off-by-One Longevity Boundaries**:
   - Exactly 12 months (0% discount) vs 13 months (10% discount).
   - Exactly 36 months (10% discount) vs 37 months (25% discount).
   - Step-function discount transitions across all tiers (`BASIC`, `PRO`, `ENTERPRISE`).

2. **Defensive Temporal Integrity (Negative Inputs)**:
   - Negative account age values (`-1`, `-2`, `-12`, `-36`, `Integer.MIN_VALUE`).
   - Rejection via `IllegalArgumentException` to prevent bypassing brackets or corrupting billing metrics.

3. **Voucher Token Sanitization & Smuggling**:
   - Whitespace-only vouchers (`"   "`, `"\t"`, `"\n"`).
   - Untrimmed valid vouchers (`"  SAVE20  "`, `"\tHALFPRICE\n"`).
   - Case-variant tokens (`"save20"`, `"halfprice"`, `"Save20"`).
   - Internal spaces and delimiters (`"SAVE 20"`, `"HALF PRICE"`, `"SAVE20\0"`).
   - Rejection via `InvalidVoucherException` to avoid ambiguous concessions or silent failures.

4. **Fractional Cent Precision & Rounding**:
   - Terminal fractional cents from compounding percentage discounts (e.g., $18.75, $56.25, $187.50).
   - Strict half-up rounding (`RoundingMode.HALF_UP`) ensuring ISO 4217 two-decimal precision.
   - Prevention of scale truncation (e.g., `$25.0` or `$25` instead of `$25.00`).

5. **Floor Price Clamping**:
   - Zero or negative resulting rates clamped strictly to `$0.00` minimum.

---

## Justifications for Updated Test Suite

### 1. Off-by-One Longevity Boundary Thresholds
- **Engineering Justification**: Step-function discounting curves in billing engines are highly susceptible to off-by-one boundary bugs (e.g., `<` vs `<=` or `>=` vs `>`). A subscription reaching exactly 12 months (365 days) must not receive loyalty pricing prematurely; the 10% discount applies strictly on the 13th monthly cycle. Similarly, month 36 is the terminal boundary of the 10% bracket, and month 37 is the entry point for 25%. Testing both sides of each inflection point across all tiers (`BASIC`, `PRO`, `ENTERPRISE`) guarantees the contractually agreed discount transition points without revenue leakage.

### 2. Defensive Negative Account Age Handling
- **Engineering Justification**: Customer account age is a strictly non-negative metric representing elapsed tenure. Negative integers (`accountAgeMonths < 0`) typically originate from corrupted database rows, inverted timestamp differences (`currentTime - futureDate`), or compromised API payloads. Silently allowing negative values through the `<= 12` check would mask severe data corruption and award base rates instead of failing fast. Defensively asserting `IllegalArgumentException` prevents poisoned inputs from propagating into financial ledgers.

### 3. Whitespace & Malformed Voucher Rejection
- **Engineering Justification**: Promotional vouchers represent legally binding concessions. Lenient parsing (such as silently ignoring whitespace, auto-trimming, or treating blank strings as no-op) introduces billing ambiguity and support disputes. If a customer enters `"  SAVE20  "` or `"save20"`, silently rejecting the discount without notice causes unexpected charges, while auto-applying malformed tokens can facilitate voucher smuggling. Forcing explicit token matches (`"SAVE20"`, `"HALFPRICE"`) and throwing `InvalidVoucherException` on any variation ensures client errors are surfaced immediately at checkout.

### 4. Rounding Accuracy on Fractional Cents
- **Engineering Justification**: Financial transactions require strict compliance with ISO 4217 (2 decimal places for USD). Compounding percentage discounts (e.g., 25% longevity followed by 50% `HALFPRICE` on BASIC: $50.00 × 0.75 × 0.50 = $18.75) generate fractional cent values. If implemented with floating-point primitives (`double`/`float`) or incorrect rounding modes, cumulative rounding errors drift downstream ledgers, causing settlement mismatches with payment processors (Stripe, Adyen). Asserting exact `RoundingMode.HALF_UP` behavior and scale of 2 guarantees deterministic accounting.
