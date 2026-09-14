package com.inv.expense;

import com.inv.TestFixtures;
import com.inv.basic.entity.TaxEntity;
import com.inv.expense.entity.ExpenseInvoice;
import com.inv.expense.service.ExpenseService;
import com.inv.purchase.entity.InputInvoice;
import com.inv.purchase.service.InputInvoiceService;
import com.inv.common.CheckCodes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ExpenseCheckTest {
    @Autowired
    ExpenseService service;
    @Autowired
    InputInvoiceService inputService;
    @Autowired
    TestFixtures fx;

    private ExpenseInvoice base(String no, TaxEntity e) {
        ExpenseInvoice x = new ExpenseInvoice();
        x.setEmployeeName("测试员工");
        x.setEmployeeNo("T-" + no);
        x.setDepartment("测试部");
        x.setInvoiceType("NORMAL");
        x.setInvoiceCode("X" + no);
        x.setInvoiceNo(no);
        x.setIssueDate(LocalDate.now());
        x.setSellerName("测试商户");
        x.setBuyerName(e.getName());
        x.setBuyerTaxNo(e.getTaxNo());
        x.setTotalAmount(new BigDecimal("100.00"));
        x.setTotalTax(new BigDecimal("13.00"));
        x.setTotalWithTax(new BigDecimal("113.00"));
        return x;
    }

    @Test
    void compliant() {
        TaxEntity e = fx.newEntity("E-OK", "91310000EOK000001");
        ExpenseInvoice x = service.upload(base("90000001", e));
        ExpenseInvoice r = service.complianceCheck(x.getId());
        assertEquals("COMPLIANT", r.getStatus());
        assertNull(r.getRiskItems());
        // 报销
        ExpenseInvoice rb = service.reimburse(Arrays.asList(x.getId()), "RB-1").get(0);
        assertEquals("REIMBURSED", rb.getStatus());
    }

    @Test
    void riskRules() {
        TaxEntity e = fx.newEntity("E-RISK", "91310000ERISK0001");
        // TITLE_MISMATCH
        ExpenseInvoice x = base("90000002", e);
        x.setBuyerTaxNo("91310000OTHER0001");
        ExpenseInvoice r = service.complianceCheck(service.upload(x).getId());
        assertEquals("RISK", r.getStatus());
        assertTrue(r.getRiskItems().contains("TITLE_MISMATCH"));

        // EXPIRED + AMOUNT_LIMIT
        ExpenseInvoice y = base("90000003", e);
        y.setIssueDate(LocalDate.now().minusDays(200));
        y.setTotalWithTax(new BigDecimal("60000.00"));
        r = service.complianceCheck(service.upload(y).getId());
        assertTrue(r.getRiskItems().contains("EXPIRED"));
        assertTrue(r.getRiskItems().contains("AMOUNT_LIMIT"));

        // DUPLICATE：与进项票同号
        InputInvoice in = new InputInvoice();
        in.setInvoiceType("NORMAL");
        in.setInvoiceCode("X90000004");
        in.setInvoiceNo("90000004");
        in.setIssueDate(LocalDate.now());
        in.setBuyerTaxNo(e.getTaxNo());
        in.setTotalAmount(new BigDecimal("100"));
        in.setTotalTax(new BigDecimal("13"));
        in.setTotalWithTax(new BigDecimal("113"));
        inputService.create(in, null, "MANUAL");
        ExpenseInvoice d = service.upload(base("90000004", e));
        r = service.complianceCheck(d.getId());
        assertTrue(r.getRiskItems().contains("DUPLICATE"));

        // VERIFY_FAILED
        ExpenseInvoice v = base("90000005", e);
        v.setCheckCode("00000000000000000001");
        r = service.complianceCheck(service.upload(v).getId());
        assertTrue(r.getRiskItems().contains("VERIFY_FAILED"));

        // RISK 不可报销
        ExpenseInvoice risk = r;
        assertThrows(com.inv.common.BizException.class,
                () -> service.reimburse(Arrays.asList(risk.getId()), "RB-2"));
    }
}
