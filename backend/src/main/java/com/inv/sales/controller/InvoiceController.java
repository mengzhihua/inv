package com.inv.sales.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inv.common.BizException;
import com.inv.common.Csv;
import com.inv.common.R;
import com.inv.sales.entity.DeliveryLog;
import com.inv.sales.entity.Invoice;
import com.inv.sales.entity.RedInfo;
import com.inv.sales.entity.RedInfoLine;
import com.inv.sales.mapper.InvoiceMapper;
import com.inv.sales.mapper.RedInfoMapper;
import com.inv.sales.service.InvoiceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class InvoiceController {
    private final InvoiceService service;
    private final InvoiceMapper mapper;
    private final RedInfoMapper redInfoMapper;

    @Data
    public static class IssueReq {
        private Long requestId;
    }

    @Data
    public static class ReasonReq {
        private String reason;
    }

    @Data
    public static class RedInfoReq {
        private Long invoiceId;
        private String reason;
        private List<RedInfoLine> lines;
    }

    @Data
    public static class DeliverReq {
        private String channel;
        private String target;
    }

    @Data
    public static class BatchDeliverReq {
        private List<Long> ids;
        private String channel;
        private String target;
    }

    // ---------- 发票查询 ----------
    @GetMapping("/invoice/page")
    public R<Page<Invoice>> page(@RequestParam(defaultValue = "1") long current,
                                 @RequestParam(defaultValue = "20") long size,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) String invoiceType,
                                 @RequestParam(required = false) Long taxEntityId,
                                 @RequestParam(required = false) String buyerName,
                                 @RequestParam(required = false) String invoiceNo,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return R.ok(mapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<Invoice>()
                .eq(StringUtils.isNotBlank(status), Invoice::getStatus, status)
                .eq(StringUtils.isNotBlank(invoiceType), Invoice::getInvoiceType, invoiceType)
                .eq(taxEntityId != null, Invoice::getTaxEntityId, taxEntityId)
                .like(StringUtils.isNotBlank(buyerName), Invoice::getBuyerName, buyerName)
                .like(StringUtils.isNotBlank(invoiceNo), Invoice::getInvoiceNo, invoiceNo)
                .ge(dateFrom != null, Invoice::getIssueDate, dateFrom)
                .le(dateTo != null, Invoice::getIssueDate, dateTo)
                .orderByDesc(Invoice::getId)));
    }

    @GetMapping("/invoice/{id}")
    public R<Map<String, Object>> get(@PathVariable Long id) {
        Map<String, Object> r = new HashMap<>();
        r.put("invoice", service.requireInvoice(id));
        r.put("lines", service.linesOf(id));
        r.put("events", service.eventsOf(id));
        r.put("deliveries", service.deliveriesOf(id));
        return R.ok(r);
    }

    @GetMapping("/invoice/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String invoiceType) {
        List<Invoice> rows = mapper.selectList(new LambdaQueryWrapper<Invoice>()
                .eq(StringUtils.isNotBlank(status), Invoice::getStatus, status)
                .eq(StringUtils.isNotBlank(invoiceType), Invoice::getInvoiceType, invoiceType)
                .orderByDesc(Invoice::getId));
        return Csv.download("invoices.csv",
                new String[]{"invoiceCode", "invoiceNo", "invoiceType", "buyerName", "issueDate",
                        "totalAmount", "totalTax", "totalWithTax", "status"},
                rows, i -> new Object[]{i.getInvoiceCode(), i.getInvoiceNo(), i.getInvoiceType(),
                        i.getBuyerName(), i.getIssueDate(), i.getTotalAmount(), i.getTotalTax(),
                        i.getTotalWithTax(), i.getStatus()});
    }

    // ---------- 开票 / 作废 / 交付 ----------
    @PostMapping("/invoice/issue")
    public R<Invoice> issue(@RequestBody IssueReq req) {
        return R.ok(service.issue(req.getRequestId()));
    }

    @PostMapping("/invoice/batch-issue")
    public R<List<Invoice>> batchIssue(@RequestBody Map<String, List<Long>> body) {
        List<Invoice> out = new ArrayList<>();
        for (Long id : body.get("requestIds")) {
            out.add(service.issue(id));
        }
        return R.ok(out);
    }

    @PostMapping("/invoice/{id}/cancel")
    public R<Invoice> cancel(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        return R.ok(service.cancel(id, req == null ? null : req.getReason()));
    }

    @PostMapping("/invoice/{id}/deliver")
    public R<DeliveryLog> deliver(@PathVariable Long id, @RequestBody DeliverReq req) {
        return R.ok(service.deliver(id, req.getChannel(), req.getTarget()));
    }

    @PostMapping("/invoice/batch-deliver")
    public R<List<DeliveryLog>> batchDeliver(@RequestBody BatchDeliverReq req) {
        List<DeliveryLog> out = new ArrayList<>();
        for (Long id : req.getIds()) {
            out.add(service.deliver(id, req.getChannel(), req.getTarget()));
        }
        return R.ok(out);
    }

    // ---------- 红冲 ----------
    @GetMapping("/red-info/page")
    public R<Page<RedInfo>> redInfoPage(@RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "20") long size,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) Long invoiceId) {
        return R.ok(redInfoMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<RedInfo>()
                .eq(StringUtils.isNotBlank(status), RedInfo::getStatus, status)
                .eq(invoiceId != null, RedInfo::getInvoiceId, invoiceId)
                .orderByDesc(RedInfo::getId)));
    }

    @PostMapping("/red-info")
    public R<RedInfo> createRedInfo(@RequestBody RedInfoReq req) {
        return R.ok(service.createRedInfo(req.getInvoiceId(), req.getReason(), req.getLines()));
    }

    @PostMapping("/red-info/{id}/confirm")
    public R<RedInfo> confirmRedInfo(@PathVariable Long id) {
        return R.ok(service.confirmRedInfo(id));
    }

    @PostMapping("/red-info/{id}/red-flush")
    public R<Invoice> redFlush(@PathVariable Long id) {
        return R.ok(service.redFlush(id));
    }
}
