package com.inv.tax;

import com.inv.TestFixtures;
import com.inv.basic.entity.TaxEntity;
import com.inv.common.CheckCodes;
import com.inv.purchase.entity.InputInvoice;
import com.inv.purchase.service.InputInvoiceService;
import com.inv.sales.entity.Invoice;
import com.inv.sales.entity.InvoiceRequest;
import com.inv.sales.service.InvoiceRequestService;
import com.inv.sales.service.InvoiceService;
import com.inv.tax.service.TaxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 增值税预填：销项净额 - 进项已抵扣 = 应纳税额 */
@SpringBootTest
@ActiveProfiles("test")
class VatReturnTest {
    @Autowired
    TaxService taxService;
    @Autowired
    InvoiceRequestService requestService;
    @Autowired
    InvoiceService invoiceService;
    @Autowired
    InputInvoiceService inputService;
    @Autowired
    TestFixtures fx;

    @Test
    void vatReturnNumbers() {
        TaxEntity e = fx.newEntity("T-VAT", "91310000TVAT00001");
        fx.stock(e.getId(), "E_NORMAL", "999000000003", "40000001", "40000010");
        String period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        taxService.open(e.getId(), period);

        // 开 2 张销项票：100@13%=13, 200@0.06=12 → 销项税 25，不含税 300
        Invoice i1 = issueOne(e, "100.00", "0.13");
        Invoice i2 = issueOne(e, "200.00", "0.06");
        // 作废其中一张重开，验证作废不计入：直接红冲 i2（部分 100@0.06=6）
        com.inv.sales.entity.RedInfoLine rl = new com.inv.sales.entity.RedInfoLine();
        rl.setGoodsName("测试商品");
        rl.setAmount(new BigDecimal("100.00"));
        rl.setTaxRate(new BigDecimal("0.06"));
        rl.setTaxAmount(new BigDecimal("6.00"));
        com.inv.sales.entity.RedInfo info = invoiceService.createRedInfo(i2.getId(), "RETURN", java.util.Collections.singletonList(rl));
        info = invoiceService.confirmRedInfo(info.getId());
        invoiceService.redFlush(info.getId());
        // 净销项：不含税 200，税 19

        // 进项：查验+勾选+确认抵扣 13 元
        InputInvoice in = new InputInvoice();
        in.setInvoiceType("E_SPECIAL");
        in.setInvoiceCode("9" + System.nanoTime() % 1000);
        in.setInvoiceNo("88" + (System.nanoTime() % 1000000));
        in.setIssueDate(LocalDate.now());
        in.setSellerName("供应商");
        in.setSellerTaxNo("91320000SELLER002X");
        in.setBuyerName(e.getName());
        in.setBuyerTaxNo(e.getTaxNo());
        in.setTotalAmount(new BigDecimal("100.00"));
        in.setTotalTax(new BigDecimal("13.00"));
        in.setTotalWithTax(new BigDecimal("113.00"));
        in.setCheckCode(CheckCodes.derive(in.getInvoiceCode(), in.getInvoiceNo(), in.getIssueDate(), in.getTotalAmount()));
        in = inputService.create(in, null, "MANUAL");
        inputService.verify(in.getId());
        inputService.check(in.getId(), period);
        inputService.confirmDeduction(e.getId(), period);

        Map<String, Object> r = taxService.vatReturn(e.getId(), period);
        assertEquals(new BigDecimal("200.00"), r.get("outputAmount"));
        assertEquals(new BigDecimal("19.00"), r.get("outputTax"));
        assertEquals(new BigDecimal("13.00"), r.get("inputTax"));
        assertEquals(new BigDecimal("6.00"), r.get("payable"));
        // 税负率 = 6/200 = 0.03
        assertEquals(0, new BigDecimal("0.030000").compareTo((BigDecimal) r.get("burdenRate")));

        // preview：再勾选 6 元进项 → 税负 0
        Map<String, Object> p = taxService.preview(e.getId(), period, new BigDecimal("6.00"));
        assertEquals(new BigDecimal("0.00"), p.get("payable"));
    }

    private Invoice issueOne(TaxEntity e, String amount, String rate) {
        InvoiceRequest r = requestService.create(TestFixtures.req(e.getId(), "E_NORMAL"), TestFixtures.one(amount, rate));
        requestService.transit(r.getId(), "submit", null);
        requestService.transit(r.getId(), "approve", null);
        return invoiceService.issue(r.getId());
    }
}
