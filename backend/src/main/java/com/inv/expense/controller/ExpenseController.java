package com.inv.expense.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inv.common.R;
import com.inv.expense.entity.ExpenseInvoice;
import com.inv.expense.mapper.ExpenseInvoiceMapper;
import com.inv.expense.service.ExpenseService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expense")
@RequiredArgsConstructor
public class ExpenseController {
    private final ExpenseService service;
    private final ExpenseInvoiceMapper mapper;

    @Data
    public static class IdsReq {
        private List<Long> ids;
    }

    @Data
    public static class ReimburseReq {
        private List<Long> ids;
        private String reimburseNo;
    }

    @Data
    public static class ReasonReq {
        private String reason;
    }

    @GetMapping("/page")
    public R<Page<ExpenseInvoice>> page(@RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "20") long size,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String department,
                                        @RequestParam(required = false) String keyword) {
        return R.ok(mapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<ExpenseInvoice>()
                .eq(StringUtils.isNotBlank(status), ExpenseInvoice::getStatus, status)
                .eq(StringUtils.isNotBlank(department), ExpenseInvoice::getDepartment, department)
                .like(StringUtils.isNotBlank(keyword), ExpenseInvoice::getEmployeeName, keyword)
                .orderByDesc(ExpenseInvoice::getId)));
    }

    @GetMapping("/{id}")
    public R<ExpenseInvoice> get(@PathVariable Long id) {
        return R.ok(service.require(id));
    }

    @PostMapping
    public R<ExpenseInvoice> upload(@RequestBody ExpenseInvoice inv) {
        return R.ok(service.upload(inv));
    }

    @PostMapping("/{id}/compliance-check")
    public R<ExpenseInvoice> check(@PathVariable Long id) {
        return R.ok(service.complianceCheck(id));
    }

    @PostMapping("/compliance-check")
    public R<List<ExpenseInvoice>> checkAll(@RequestBody(required = false) IdsReq req) {
        return R.ok(service.complianceCheckAll(req == null ? null : req.getIds()));
    }

    @PostMapping("/reimburse")
    public R<List<ExpenseInvoice>> reimburse(@RequestBody ReimburseReq req) {
        return R.ok(service.reimburse(req.getIds(), req.getReimburseNo()));
    }

    @PostMapping("/{id}/reject")
    public R<ExpenseInvoice> reject(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        return R.ok(service.reject(id, req == null ? null : req.getReason()));
    }
}
