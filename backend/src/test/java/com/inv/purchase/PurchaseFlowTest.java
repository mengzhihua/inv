package com.inv.purchase;

import com.inv.TestFixtures;
import com.inv.basic.entity.TaxEntity;
import com.inv.common.BizException;
import com.inv.common.CheckCodes;
import com.inv.purchase.entity.DeductionBatch;
import com.inv.purchase.entity.InputInvoice;
import com.inv.purchase.service.InputInvoiceService;
import com.inv.tax.service.TaxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PurchaseFlowTest {
    @Autowired
    InputInvoiceService service;
    @Autowired
    TaxService taxService;
    @Autowired
    TestFixtures fx;

    private InputInvoice inv(TaxEntity e, String no, String amount, String tax) {
        InputInvoice i = new InputInvoice();
        i.setInvoiceType("SPECIAL");
        i.setInvoiceCode("032002300" + no.substring(0, 3));
        i.setInvoiceNo(no);
        i.setIssueDate(LocalDate.now());
        i.setSellerName("测试供应商");
        i.setSellerTaxNo("91320000SELLER001X");
        i.setBuyerName(e.getName());
        i.setBuyerTaxNo(e.getTaxNo());
        i.setTotalAmount(new BigDecimal(amount));
        i.setTotalTax(new BigDecimal(tax));
        i.setTotalWithTax(new BigDecimal(amount).add(new BigDecimal(tax)));
        i.setCheckCode(CheckCodes.derive(i.getInvoiceCode(), no, i.getIssueDate(), i.getTotalAmount()));
        return i;
    }

    @Test
    void dupCheck() {
        TaxEntity e = fx.newEntity("P-DUP", "91310000PDUP00001");
        service.create(inv(e, "80000001", "100.00", "13.00"), null, "MANUAL");
        BizException ex = assertThrows(BizException.class,
                () -> service.create(inv(e, "80000001", "100.00", "13.00"), null, "MANUAL"));
        assertTrue(ex.getMessage().contains("DUPLICATE"));
    }

    @Test
    void verifyFlow() {
        TaxEntity e = fx.newEntity("P-VFY", "91310000PVFY00001");
        InputInvoice good = service.create(inv(e, "80000002", "100.00", "13.00"), null, "MANUAL");
        assertEquals(e.getId(), good.getTaxEntityId()); // buyerTaxNo 自动匹配主体
        InputInvoice v = service.verify(good.getId());
        assertEquals("VERIFIED", v.getVerifyStatus());
        assertEquals("NORMAL", v.getStatus());

        // 校验码错误 → FAILED
        InputInvoice bad = inv(e, "80000003", "100.00", "13.00");
        bad.setCheckCode("00000000000000000001");
        bad = service.create(bad, null, "MANUAL");
        assertEquals("FAILED", service.verify(bad.getId()).getVerifyStatus());

        // 抬头不符 → VERIFIED + ABNORMAL
        InputInvoice wrong = inv(e, "80000004", "100.00", "13.00");
        wrong.setBuyerTaxNo("91310000OTHER0001");
        wrong.setCheckCode(CheckCodes.derive(wrong.getInvoiceCode(), "80000004", wrong.getIssueDate(), wrong.getTotalAmount()));
        wrong = service.create(wrong, null, "MANUAL");
        InputInvoice vw = service.verify(wrong.getId());
        assertEquals("VERIFIED", vw.getVerifyStatus());
        assertEquals("ABNORMAL", vw.getStatus());
    }

    @Test
    void checkDeductAndPeriodClose() {
        TaxEntity e = fx.newEntity("P-CHK", "91310000PCHK00001");
        String period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        taxService.open(e.getId(), period);

        InputInvoice a = service.create(inv(e, "80000005", "100.00", "13.00"), null, "MANUAL");
        // 未查验不可勾选
        assertThrows(BizException.class, () -> service.check(a.getId(), period));
        service.verify(a.getId());
        InputInvoice checked = service.check(a.getId(), period);
        assertEquals("CHECKED", checked.getDeductStatus());
        assertEquals(period, checked.getDeductPeriod());

        // 关账后不可再勾选
        InputInvoice b = service.create(inv(e, "80000006", "200.00", "26.00"), null, "MANUAL");
        service.verify(b.getId());
        taxService.close(e.getId(), period);
        assertThrows(BizException.class, () -> service.check(b.getId(), period));
        // 已勾选的也不能在该期确认抵扣
        assertThrows(BizException.class, () -> service.confirmDeduction(e.getId(), period));
        // 重新开账后可确认
        taxService.open(e.getId(), period);
        DeductionBatch batch = service.confirmDeduction(e.getId(), period);
        assertEquals(1, batch.getInvoiceCount().intValue());
        assertEquals(new BigDecimal("13.00"), batch.getTotalTax());
        assertEquals("DEDUCTED", service.require(a.getId()).getDeductStatus());

        // 普票不可勾选
        InputInvoice normal = inv(e, "80000007", "50.00", "0.00");
        normal.setInvoiceType("NORMAL");
        normal = service.create(normal, null, "MANUAL");
        InputInvoice finalNormal = normal;
        assertThrows(BizException.class, () -> service.check(finalNormal.getId(), period));
    }

    @Test
    void matchAndPost() {
        TaxEntity e = fx.newEntity("P-MCH", "91310000PMCH00001");
        InputInvoice inv = service.create(inv(e, "80000008", "100.00", "13.00"), null, "MANUAL");
        InputInvoice m = service.match(inv.getId(), "PO-1", "RC-1", new BigDecimal("113.00"));
        assertEquals("MATCHED", m.getMatchStatus());
        InputInvoice m2 = service.match(inv.getId(), "PO-1", "RC-1", new BigDecimal("200.00"));
        assertEquals("MISMATCH", m2.getMatchStatus());
        InputInvoice posted = service.post(inv.getId());
        assertEquals("POSTED", posted.getAccountStatus());
        assertNotNull(posted.getVoucherNo());
    }
}
