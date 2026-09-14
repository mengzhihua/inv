package com.inv.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 税额计算：BigDecimal HALF_UP，与税局一致的 ±0.06 容差 */
public final class TaxCalc {
    /** 合计容差（与税局一致） */
    public static final BigDecimal TOLERANCE = new BigDecimal("0.06");

    private TaxCalc() {
    }

    public static BigDecimal round(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** 税额 = round(amount * rate, 2) */
    public static BigDecimal taxOf(BigDecimal amount, BigDecimal rate) {
        if (amount == null || rate == null) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /** 含税反算：不含税 = round(含税/(1+rate), 2)；税额 = 含税 - 不含税 */
    public static BigDecimal[] fromIncludeTax(BigDecimal withTax, BigDecimal rate) {
        BigDecimal amount = withTax.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
        return new BigDecimal[]{amount, withTax.subtract(amount)};
    }

    public static boolean withinTolerance(BigDecimal declared, BigDecimal computed) {
        if (declared == null) {
            return true;
        }
        return declared.subtract(computed).abs().compareTo(TOLERANCE) <= 0;
    }

    /** 纳税人识别号：15/17/18/20 位数字或大写字母 */
    public static boolean validTaxNo(String taxNo) {
        return taxNo != null && taxNo.matches("^[0-9A-Z]{15}$|^[0-9A-Z]{17,18}$|^[0-9A-Z]{20}$");
    }
}
