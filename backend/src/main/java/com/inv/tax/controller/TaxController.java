package com.inv.tax.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inv.common.R;
import com.inv.tax.entity.TaxPeriod;
import com.inv.tax.mapper.TaxPeriodMapper;
import com.inv.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/tax")
@RequiredArgsConstructor
public class TaxController {
    private final TaxService taxService;
    private final TaxPeriodMapper periodMapper;

    @GetMapping("/period/page")
    public R<Page<TaxPeriod>> periods(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "20") long size,
                                      @RequestParam(required = false) Long taxEntityId,
                                      @RequestParam(required = false) String status) {
        return R.ok(periodMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<TaxPeriod>()
                        .eq(taxEntityId != null, TaxPeriod::getTaxEntityId, taxEntityId)
                        .eq(status != null, TaxPeriod::getStatus, status)
                        .orderByDesc(TaxPeriod::getPeriod)));
    }

    @PostMapping("/period/close")
    public R<TaxPeriod> close(@RequestBody Map<String, Object> body) {
        return R.ok(taxService.close(numId(body.get("taxEntityId")), String.valueOf(body.get("period"))));
    }

    @PostMapping("/period/open")
    public R<TaxPeriod> open(@RequestBody Map<String, Object> body) {
        return R.ok(taxService.open(numId(body.get("taxEntityId")), String.valueOf(body.get("period"))));
    }

    @GetMapping("/vat-return")
    public R<Map<String, Object>> vatReturn(@RequestParam Long entity, @RequestParam String period) {
        return R.ok(taxService.vatReturn(entity, period));
    }

    @GetMapping("/vat-return/preview")
    public R<Map<String, Object>> preview(@RequestParam Long entity, @RequestParam String period,
                                          @RequestParam(required = false) BigDecimal additionalInputTax) {
        return R.ok(taxService.preview(entity, period, additionalInputTax));
    }

    private static Long numId(Object o) {
        return o == null ? null : Long.valueOf(String.valueOf(o));
    }
}
