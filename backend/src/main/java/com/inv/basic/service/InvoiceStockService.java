package com.inv.basic.service;

import com.inv.basic.entity.InvoiceStock;
import com.inv.basic.mapper.InvoiceStockMapper;
import com.inv.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 发票号段取号：原子 UPDATE ... WHERE remaining>0 并校验影响行数 */
@Service
@RequiredArgsConstructor
public class InvoiceStockService {
    private final InvoiceStockMapper stockMapper;
    private final BasicService basicService;
    private final JdbcTemplate jdbc;

    /**
     * 按主体+票种取下一个发票号码。
     * 事务内 SELECT ... FOR UPDATE 锁行，再原子 UPDATE ... WHERE remaining>0 并校验影响行数。
     */
    public IssuedNo takeNumber(Long taxEntityId, String invoiceType) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, invoice_code, start_no, end_no, current_no, remaining FROM inv_invoice_stock"
                        + " WHERE tax_entity_id = ? AND invoice_type = ? AND status = 1 ORDER BY id LIMIT 1 FOR UPDATE",
                taxEntityId, invoiceType);
        if (rows.isEmpty()) {
            throw new BizException("主体无可用 " + invoiceType + " 发票号段，请先领用");
        }
        Map<String, Object> s = rows.get(0);
        Number remaining = (Number) s.get("remaining");
        if (remaining == null || remaining.intValue() <= 0) {
            throw new BizException("发票号段已用完（" + s.get("invoice_code") + " "
                    + s.get("start_no") + "-" + s.get("end_no") + "）");
        }
        String current = String.valueOf(s.get("current_no"));
        String next = increment(current, String.valueOf(s.get("end_no")));
        int updated = jdbc.update(
                "UPDATE inv_invoice_stock SET current_no = ?, remaining = remaining - 1"
                        + " WHERE id = ? AND remaining > 0",
                next, ((Number) s.get("id")).longValue());
        if (updated != 1) {
            throw new BizException("取号失败：号段余量不足或已被取走");
        }
        return new IssuedNo((String) s.get("invoice_code"), current);
    }

    private static String increment(String current, String endNo) {
        long cur;
        try {
            cur = Long.parseLong(current);
        } catch (NumberFormatException e) {
            throw new BizException("号段当前号码非法: " + current);
        }
        long end = Long.parseLong(endNo);
        if (cur >= end) {
            // 已是最后一张：下一张仍写 endNo+1，由 remaining>0 兜底拦截
        }
        return String.format("%0" + current.length() + "d", cur + 1);
    }

    public static final class IssuedNo {
        private final String invoiceCode;
        private final String invoiceNo;

        IssuedNo(String invoiceCode, String invoiceNo) {
            this.invoiceCode = invoiceCode;
            this.invoiceNo = invoiceNo;
        }

        public String getInvoiceCode() {
            return invoiceCode;
        }

        public String getInvoiceNo() {
            return invoiceNo;
        }
    }
}
