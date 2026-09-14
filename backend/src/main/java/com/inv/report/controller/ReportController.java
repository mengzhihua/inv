package com.inv.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inv.common.BizException;
import com.inv.common.Csv;
import com.inv.common.R;
import com.inv.expense.entity.ExpenseInvoice;
import com.inv.expense.mapper.ExpenseInvoiceMapper;
import com.inv.purchase.entity.InputInvoice;
import com.inv.purchase.mapper.InputInvoiceMapper;
import com.inv.basic.mapper.InvoiceStockMapper;
import com.inv.basic.entity.InvoiceStock;
import com.inv.sales.mapper.InvoiceMapper;
import com.inv.sales.mapper.InvoiceRequestMapper;
import com.inv.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 报表：工作台 + 销项/进项统计 + 红冲作废统计 + 客户排名 */
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {
    private final InvoiceRequestMapper requestMapper;
    private final InvoiceMapper invoiceMapper;
    private final InputInvoiceMapper inputMapper;
    private final ExpenseInvoiceMapper expenseMapper;
    private final InvoiceStockMapper stockMapper;
    private final TaxService taxService;
    private final JdbcTemplate jdbc;

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        Map<String, Object> r = new HashMap<>();
        String today = LocalDate.now().toString();

        r.put("pendingSubmit", requestMapper.selectCount(new QueryWrapper<com.inv.sales.entity.InvoiceRequest>()
                .eq("status", "SUBMITTED")));
        r.put("pendingIssue", requestMapper.selectCount(new QueryWrapper<com.inv.sales.entity.InvoiceRequest>()
                .eq("status", "APPROVED")));
        r.put("todayInvoices", invoiceMapper.selectCount(new QueryWrapper<com.inv.sales.entity.Invoice>()
                .eq("issue_date", today).ne("status", "CANCELLED")));
        BigDecimal todayAmount = sum("SELECT COALESCE(SUM(total_with_tax),0) FROM inv_invoice WHERE issue_date = CURRENT_DATE AND status <> 'CANCELLED'");
        r.put("todayAmount", todayAmount);
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate nextMonth = monthStart.plusMonths(1);
        r.put("monthOutputTax", jdbc.queryForObject("SELECT COALESCE(SUM(total_tax),0) FROM inv_invoice WHERE issue_date >= ? AND issue_date < ? AND status <> 'CANCELLED'",
                BigDecimal.class, monthStart, nextMonth));
        r.put("monthInputTax", jdbc.queryForObject("SELECT COALESCE(SUM(total_tax),0) FROM inv_input_invoice WHERE issue_date >= ? AND issue_date < ? AND status <> 'RED_FLUSHED'",
                BigDecimal.class, monthStart, nextMonth));
        r.put("pendingVerify", inputMapper.selectCount(new QueryWrapper<InputInvoice>().eq("verify_status", "UNVERIFIED")));
        r.put("abnormalInvoices", inputMapper.selectCount(new QueryWrapper<InputInvoice>().eq("status", "ABNORMAL")));
        r.put("pendingCheck", inputMapper.selectCount(new QueryWrapper<InputInvoice>().eq("deduct_status", "PENDING").eq("verify_status", "VERIFIED")));
        r.put("expenseRisk", expenseMapper.selectCount(new QueryWrapper<ExpenseInvoice>().eq("status", "RISK")));

        // 号段余量预警（remaining < 50）
        List<InvoiceStock> low = stockMapper.selectList(new QueryWrapper<InvoiceStock>()
                .eq("status", 1).lt("remaining", 50));
        r.put("lowStock", low);

        // 税负率（当前属期，首个主体）
        // 12 月趋势：每月开票张数与金额
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            LocalDate first = LocalDate.now().minusMonths(i).withDayOfMonth(1);
            LocalDate last = first.plusMonths(1);
            Map<String, Object> row = new HashMap<>();
            row.put("month", first.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            row.put("count", invoiceMapper.selectCount(new QueryWrapper<com.inv.sales.entity.Invoice>()
                    .ge("issue_date", first).lt("issue_date", last).ne("status", "CANCELLED")));
            row.put("amount", jdbc.queryForObject(
                    "SELECT COALESCE(SUM(total_with_tax),0) FROM inv_invoice WHERE issue_date >= ? AND issue_date < ? AND status <> 'CANCELLED'",
                    BigDecimal.class, first, last));
            trend.add(row);
        }
        r.put("trend", trend);
        return R.ok(r);
    }

    /** 销项统计：维度 dimension = entity/customer/type/month */
    @GetMapping("/sales-summary")
    public R<List<Map<String, Object>>> salesSummary(@RequestParam(defaultValue = "month") String dimension,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BizException("开始日期不能晚于结束日期");
        }
        if (to != null && LocalDate.MAX.equals(to)) {
            throw new BizException("结束日期非法");
        }
        if ("month".equals(dimension)) {
            boolean defaultRange = from == null && to == null;
            LocalDate rangeFrom = defaultRange
                    ? LocalDate.now().minusMonths(11).withDayOfMonth(1)
                    : (from == null ? LocalDate.now().minusMonths(11).withDayOfMonth(1) : from);
            LocalDate exclusiveTo = defaultRange
                    ? LocalDate.now().plusMonths(1).withDayOfMonth(1)
                    : (to == null ? LocalDate.now().plusDays(1) : to.plusDays(1));
            LocalDate first = rangeFrom.withDayOfMonth(1);
            List<Map<String, Object>> rows = new ArrayList<>();
            while (first.isBefore(exclusiveTo)) {
                LocalDate next = first.plusMonths(1);
                LocalDate queryFrom = first.isAfter(rangeFrom) ? first : rangeFrom;
                LocalDate queryTo = next.isBefore(exclusiveTo) ? next : exclusiveTo;
                if (queryFrom.isBefore(queryTo)) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("dim", first.format(DateTimeFormatter.ofPattern("yyyy-MM")));
                    row.put("cnt", jdbc.queryForObject("SELECT COUNT(*) FROM inv_invoice WHERE issue_date >= ? AND issue_date < ? AND status <> 'CANCELLED'", Long.class, queryFrom, queryTo));
                    row.put("amount", jdbc.queryForObject("SELECT COALESCE(SUM(total_amount),0) FROM inv_invoice WHERE issue_date >= ? AND issue_date < ? AND status <> 'CANCELLED'", BigDecimal.class, queryFrom, queryTo));
                    row.put("tax", jdbc.queryForObject("SELECT COALESCE(SUM(total_tax),0) FROM inv_invoice WHERE issue_date >= ? AND issue_date < ? AND status <> 'CANCELLED'", BigDecimal.class, queryFrom, queryTo));
                    row.put("withTax", jdbc.queryForObject("SELECT COALESCE(SUM(total_with_tax),0) FROM inv_invoice WHERE issue_date >= ? AND issue_date < ? AND status <> 'CANCELLED'", BigDecimal.class, queryFrom, queryTo));
                    rows.add(row);
                }
                first = next;
            }
            return R.ok(rows);
        }
        String col;
        switch (dimension) {
            case "entity": col = "tax_entity_id"; break;
            case "customer": col = "buyer_name"; break;
            case "type": col = "invoice_type"; break;
            default: col = "buyer_name"; break;
        }
        QueryWrapper<com.inv.sales.entity.Invoice> qw = new QueryWrapper<>();
        qw.select(col + " AS dim", "COUNT(*) AS cnt", "SUM(total_amount) AS amount",
                        "SUM(total_tax) AS tax", "SUM(total_with_tax) AS withTax")
                .ne("status", "CANCELLED")
                .ge(from != null, "issue_date", from)
                .le(to != null, "issue_date", to)
                .groupBy(col)
                .orderByAsc("dim");
        return R.ok(invoiceMapper.selectMaps(qw));
    }

    /** 进项统计：维度 = supplier/rate/deductStatus */
    @GetMapping("/input-summary")
    public R<List<Map<String, Object>>> inputSummary(@RequestParam(defaultValue = "supplier") String dimension) {
        String col;
        switch (dimension) {
            case "deductStatus": col = "deduct_status"; break;
            case "type": col = "invoice_type"; break;
            default: col = "seller_name"; break;
        }
        QueryWrapper<InputInvoice> qw = new QueryWrapper<>();
        qw.select(col + " AS dim", "COUNT(*) AS cnt", "SUM(total_amount) AS amount",
                        "SUM(total_tax) AS tax", "SUM(total_with_tax) AS withTax")
                .groupBy(col).orderByAsc("dim");
        return R.ok(inputMapper.selectMaps(qw));
    }

    /** 红冲/作废统计 */
    @GetMapping("/red-cancel-summary")
    public R<List<Map<String, Object>>> redCancel() {
        QueryWrapper<com.inv.sales.entity.Invoice> qw = new QueryWrapper<>();
        qw.select("status AS dim", "COUNT(*) AS cnt", "SUM(total_with_tax) AS withTax")
                .in("status", "CANCELLED", "RED_FLUSHED", "RED")
                .groupBy("status");
        return R.ok(invoiceMapper.selectMaps(qw));
    }

    /** 客户开票排名 */
    @GetMapping("/customer-rank")
    public R<List<Map<String, Object>>> customerRank(@RequestParam(defaultValue = "10") int limit) {
        QueryWrapper<com.inv.sales.entity.Invoice> qw = new QueryWrapper<>();
        int n = Math.max(1, Math.min(limit, 100));
        Page<Map<String, Object>> page = new Page<>(1, n);
        qw.select("buyer_name AS dim", "COUNT(*) AS cnt", "SUM(total_with_tax) AS withTax")
                .ne("status", "CANCELLED")
                .groupBy("buyer_name")
                .orderByDesc("withTax");
        return R.ok(invoiceMapper.selectMapsPage(page, qw).getRecords());
    }

    @GetMapping("/sales-summary/export")
    public ResponseEntity<byte[]> exportSummary(@RequestParam(defaultValue = "month") String dimension) {
        List<Map<String, Object>> rows = salesSummary(dimension, null, null).getData();
        return Csv.download("sales-summary.csv",
                new String[]{"dim", "cnt", "amount", "tax", "withTax"},
                rows, r -> new Object[]{r.get("DIM") == null ? r.get("dim") : r.get("DIM"),
                        r.get("CNT") == null ? r.get("cnt") : r.get("CNT"),
                        r.get("AMOUNT") == null ? r.get("amount") : r.get("AMOUNT"),
                        r.get("TAX") == null ? r.get("tax") : r.get("TAX"),
                        r.get("WITHTAX") == null ? r.get("withTax") : r.get("WITHTAX")});
    }

    private BigDecimal sum(String sql) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class);
        return v == null ? BigDecimal.ZERO : v;
    }
}
