package com.inv.sales.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inv.common.R;
import com.inv.sales.entity.InvoiceRequest;
import com.inv.sales.entity.InvoiceRequestLine;
import com.inv.sales.mapper.InvoiceRequestMapper;
import com.inv.sales.service.InvoiceRequestService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales/request")
@RequiredArgsConstructor
public class InvoiceRequestController {
    private final InvoiceRequestService service;
    private final InvoiceRequestMapper mapper;

    @Data
    public static class RequestForm {
        private InvoiceRequest request;
        private List<InvoiceRequestLine> lines;
    }

    @GetMapping("/page")
    public R<Page<InvoiceRequest>> page(@RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "20") long size,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String invoiceType,
                                        @RequestParam(required = false) Long taxEntityId,
                                        @RequestParam(required = false) Long customerId,
                                        @RequestParam(required = false) String keyword) {
        return R.ok(mapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<InvoiceRequest>()
                .eq(StringUtils.isNotBlank(status), InvoiceRequest::getStatus, status)
                .eq(StringUtils.isNotBlank(invoiceType), InvoiceRequest::getInvoiceType, invoiceType)
                .eq(taxEntityId != null, InvoiceRequest::getTaxEntityId, taxEntityId)
                .eq(customerId != null, InvoiceRequest::getCustomerId, customerId)
                .like(StringUtils.isNotBlank(keyword), InvoiceRequest::getBuyerName, keyword)
                .orderByDesc(InvoiceRequest::getId)));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> get(@PathVariable Long id) {
        Map<String, Object> r = new HashMap<>();
        r.put("request", service.require(id));
        r.put("lines", service.linesOf(id));
        return R.ok(r);
    }

    @PostMapping
    public R<InvoiceRequest> create(@RequestBody RequestForm form) {
        return R.ok(service.create(form.getRequest(), form.getLines()));
    }

    @PutMapping("/{id}")
    public R<InvoiceRequest> update(@PathVariable Long id, @RequestBody RequestForm form) {
        return R.ok(service.update(id, form.getRequest(), form.getLines()));
    }

    @PostMapping("/{id}/submit")
    public R<InvoiceRequest> submit(@PathVariable Long id) {
        return R.ok(service.transit(id, "submit", null));
    }

    @PostMapping("/{id}/approve")
    public R<InvoiceRequest> approve(@PathVariable Long id) {
        return R.ok(service.transit(id, "approve", null));
    }

    @PostMapping("/{id}/reject")
    public R<InvoiceRequest> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return R.ok(service.transit(id, "reject", body == null ? null : body.get("reason")));
    }

    @PostMapping("/{id}/cancel")
    public R<InvoiceRequest> cancel(@PathVariable Long id) {
        return R.ok(service.transit(id, "cancel", null));
    }

    @PostMapping("/{id}/split")
    public R<List<InvoiceRequest>> split(@PathVariable Long id) {
        return R.ok(service.split(id));
    }

    @PostMapping("/merge")
    public R<InvoiceRequest> merge(@RequestBody Map<String, List<Long>> body) {
        return R.ok(service.merge(body.get("ids")));
    }

    @PostMapping("/batch-approve")
    public R<List<InvoiceRequest>> batchApprove(@RequestBody Map<String, List<Long>> body) {
        List<InvoiceRequest> out = new java.util.ArrayList<>();
        for (Long id : body.get("ids")) {
            out.add(service.transit(id, "approve", null));
        }
        return R.ok(out);
    }
}
