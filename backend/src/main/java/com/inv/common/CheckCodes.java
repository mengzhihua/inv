package com.inv.common;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 校验码模拟：sha256(code|no|date|amount) 取数字凑足 20 位 */
public final class CheckCodes {
    private CheckCodes() {
    }

    public static String derive(String invoiceCode, String invoiceNo, Object issueDate, BigDecimal amount) {
        String seed = String.valueOf(invoiceCode) + '|' + invoiceNo + '|' + issueDate + '|' + amount;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(seed.getBytes("UTF-8"));
            StringBuilder digits = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(b & 0xff);
                for (char c : h.toCharArray()) {
                    if (Character.isDigit(c)) {
                        digits.append(c);
                    }
                }
                if (digits.length() >= 20) {
                    break;
                }
            }
            // 数字不足 20 位时（概率极低）用字节值补足
            for (int i = 0; digits.length() < 20 && i < hash.length; i++) {
                digits.append(hash[i] & 0xff);
            }
            return digits.substring(0, 20);
        } catch (java.io.UnsupportedEncodingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
