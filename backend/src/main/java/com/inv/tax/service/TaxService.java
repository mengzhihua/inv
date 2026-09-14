package com.inv.tax.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inv.common.BizException;
import com.inv.common.TaxCalc;
import com.inv.purchase.entity.InputInvoice;
import com.inv.purchase.mapper.InputInvoiceMapper;
import com.inv.sales.entity.Invoice;
import com.inv.sales.mapper.InvoiceMapper;
import com.inv.tax.entity.TaxPeriod;
import com.inv.tax.mapper.TaxPeriodMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 税务：属期管理、增值税申报预填、税负预警 */
@Service
@RequiredArgsConstructor
public class TaxService {
    private final TaxPeriodMapper periodMapper;
    private final InvoiceMapper invoiceMapper;
    private final InputInvoiceMapper inputInvoiceMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Value("${inv.tax.burden-warn:0.03}")
    private BigDecimal burdenWarn;

    public String currentPeriod() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    public TaxPeriod findPeriod(Long taxEntityId, String period) {
        return periodMapper.selectOne(new LambdaQueryWrapper<TaxPeriod>()
                .eq(TaxPeriod::getTaxEntityId, taxEntityId)
                .eq(TaxPeriod::getPeriod, period));
    }

    /** 断言属期未关账（销项作废/进项勾选等调用）；属期不存在视为 OPEN */
    public void requireOpen(Long taxEntityId, String period) {
        TaxPeriod p = findPeriod(taxEntityId, period);
        if (p != null && "CLOSED".equals(p.getStatus())) {
            throw new BizException("属期 " + period + " 已关账，不允许该操作");
        }
    }

    @Transactional
    public TaxPeriod close(Long taxEntityId, String period) {
        TaxPeriod p = findPeriod(taxEntityId, period);
        if (p == null) {
            p = new TaxPeriod();
            p.setTaxEntityId(taxEntityId);
            p.setPeriod(period);
            p.setStatus("CLOSED");
            periodMapper.insert(p);
        } else {
            p.setStatus("CLOSED");
            periodMapper.updateById(p);
        }
        return p;
    }

    @Transactional
    public TaxPeriod open(Long taxEntityId, String period) {
        TaxPeriod p = findPeriod(taxEntityId, period);
        if (p == null) {
            p = new TaxPeriod();
            p.setTaxEntityId(taxEntityId);
            p.setPeriod(period);
            p.setStatus("OPEN");
            periodMapper.insert(p);
        } else {
            p.setStatus("OPEN");
            periodMapper.updateById(p);
        }
        return p;
    }

    /** 增值税申报预填 */
    public Map<String, Object> vatReturn(Long taxEntityId, String period) {
        // 销项：按税率分组（正常票 - 红字票，不含作废）；红字行金额为负，直接累加即净额
        LocalDate periodStart = LocalDate.parse(period + "-01");
        LocalDate periodEnd = periodStart.plusMonths(1);
        List<Map<String, Object>> outputByRate = jdbc.queryForList(
                "SELECT l.tax_rate AS taxRate, COUNT(DISTINCT i.id) AS invoiceCount,"
                        + " SUM(l.amount) AS amount, SUM(l.tax_amount) AS tax"
                        + " FROM inv_invoice_line l JOIN inv_invoice i ON i.id = l.invoice_id"
                        + " WHERE i.tax_entity_id = ? AND i.status <> 'CANCELLED'"
                        + " AND i.issue_date >= ? AND i.issue_date < ?"
                        + " GROUP BY l.tax_rate ORDER BY l.tax_rate",
                taxEntityId, periodStart, periodEnd);

        BigDecimal outAmount = BigDecimal.ZERO, outTax = BigDecimal.ZERO;
        int outCount = 0;
        for (Map<String, Object> m : outputByRate) {
            outAmount = outAmount.add(toBd(m.get("amount")));
            outTax = outTax.add(toBd(m.get("tax")));
            outCount += ((Number) m.get("invoiceCount")).intValue();
        }

        // 进项：本期已抵扣
        List<InputInvoice> deducted = inputInvoiceMapper.selectList(new LambdaQueryWrapper<InputInvoice>()
                .eq(InputInvoice::getTaxEntityId, taxEntityId)
                .eq(InputInvoice::getDeductStatus, "DEDUCTED")
                .eq(InputInvoice::getDeductPeriod, period));
        BigDecimal inAmount = BigDecimal.ZERO, inTax = BigDecimal.ZERO;
        for (InputInvoice in : deducted) {
            inAmount = inAmount.add(null2(in.getTotalAmount()));
            inTax = inTax.add(null2(in.getTotalTax()));
        }

        BigDecimal payable = outTax.subtract(inTax);
        BigDecimal burden = outAmount.signum() == 0 ? BigDecimal.ZERO
                : payable.divide(outAmount, 6, RoundingMode.HALF_UP);

        Map<String, Object> r = new HashMap<>();
        r.put("taxEntityId", taxEntityId);
        r.put("period", period);
        r.put("outputByRate", outputByRate);
        r.put("outputCount", outCount);
        r.put("outputAmount", TaxCalc.round(outAmount));
        r.put("outputTax", TaxCalc.round(outTax));
        r.put("inputCount", deducted.size());
        r.put("inputAmount", TaxCalc.round(inAmount));
        r.put("inputTax", TaxCalc.round(inTax));
        r.put("payable", TaxCalc.round(payable));
        r.put("credit", payable.signum() < 0 ? TaxCalc.round(payable.negate()) : BigDecimal.ZERO);
        r.put("burdenRate", burden);
        r.put("burdenWarn", burdenWarn);
        r.put("burdenWarned", outAmount.signum() > 0 && burden.compareTo(burdenWarn) < 0);
        return r;
    }

    /** 预演：若本期再勾选 additionalInputTax 元进项税后的税负率 */
    public Map<String, Object> preview(Long taxEntityId, String period, BigDecimal additionalInputTax) {
        Map<String, Object> base = vatReturn(taxEntityId, period);
        BigDecimal outAmount = (BigDecimal) base.get("outputAmount");
        BigDecimal outTax = (BigDecimal) base.get("outputTax");
        BigDecimal inTax = ((BigDecimal) base.get("inputTax")).add(
                additionalInputTax == null ? BigDecimal.ZERO : additionalInputTax);
        BigDecimal payable = outTax.subtract(inTax);
        BigDecimal burden = outAmount.signum() == 0 ? BigDecimal.ZERO
                : payable.divide(outAmount, 6, RoundingMode.HALF_UP);
        Map<String, Object> r = new HashMap<>();
        r.put("period", period);
        r.put("currentBurdenRate", base.get("burdenRate"));
        r.put("additionalInputTax", additionalInputTax);
        r.put("payable", TaxCalc.round(payable));
        r.put("burdenRate", burden);
        r.put("burdenWarned", outAmount.signum() > 0 && burden.compareTo(burdenWarn) < 0);
        return r;
    }

    static BigDecimal toBd(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        }
        return new BigDecimal(String.valueOf(o));
    }

    static BigDecimal null2(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
