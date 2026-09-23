package com.inv.purchase.service;

import java.math.BigDecimal;

/** 进项发票、采购订单金额、收货数量三单比对。缺采购金额或收货数量时不算匹配。 */
public final class ThreeWayMatch {
    public static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");

    private ThreeWayMatch() {
    }

    public static String judge(BigDecimal invoiceAmount, BigDecimal poAmount,
                               BigDecimal invoiceQty, BigDecimal receivedQty) {
        if (poAmount == null || receivedQty == null || invoiceAmount == null || invoiceQty == null) {
            return "UNMATCHED";
        }
        boolean amountOk = invoiceAmount.subtract(poAmount).abs().compareTo(AMOUNT_TOLERANCE) <= 0;
        boolean qtyOk = invoiceQty.compareTo(receivedQty) == 0;
        return amountOk && qtyOk ? "MATCHED" : "MISMATCH";
    }
}
