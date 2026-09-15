package com.inv.report;

import com.inv.TestFixtures;
import com.inv.basic.entity.TaxEntity;
import com.inv.common.BizException;
import com.inv.report.controller.ReportController;
import com.inv.sales.entity.Invoice;
import com.inv.sales.entity.InvoiceRequest;
import com.inv.sales.service.InvoiceRequestService;
import com.inv.sales.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class ReportTest {

    @Autowired
    ReportController reportController;
    @Autowired
    InvoiceRequestService requestService;
    @Autowired
    InvoiceService invoiceService;
    @Autowired
    TestFixtures fx;
    @Autowired
    com.inv.sales.mapper.InvoiceMapper invoiceMapper;

    @Test
    void monthlySalesSummaryRespectsPartialDateRange() {
        TaxEntity entity = fx.newEntity("T-REPORT-DATE", "91310000TREPORT01");
        fx.stock(entity.getId(), "NORMAL", "999000000031", "50000001", "50000100");

        Invoice before = issue(entity, "500.00");
        before.setIssueDate(LocalDate.of(2020, 3, 10));
        invoiceMapper.updateById(before);

        Invoice inRange1 = issue(entity, "600.00");
        inRange1.setIssueDate(LocalDate.of(2020, 3, 15));
        invoiceMapper.updateById(inRange1);

        Invoice inRange2 = issue(entity, "700.00");
        inRange2.setIssueDate(LocalDate.of(2020, 3, 20));
        invoiceMapper.updateById(inRange2);

        Invoice after = issue(entity, "800.00");
        after.setIssueDate(LocalDate.of(2020, 3, 21));
        invoiceMapper.updateById(after);

        List<Map<String, Object>> rows = reportController.salesSummary(
                "month", LocalDate.of(2020, 3, 15), LocalDate.of(2020, 3, 20)).getData();
        assertNotNull(rows);
        assertEquals(1, rows.size());
        assertEquals(2L, ((Number) rows.get(0).get("cnt")).longValue());
        assertEquals(new BigDecimal("1300.00"), new BigDecimal(String.valueOf(rows.get(0).get("amount"))));
    }

    @Test
    void rejectsInvalidSalesSummaryDateRange() {
        BizException reversed = assertThrows(BizException.class, () -> reportController.salesSummary(
                "month", LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 15)));
        assertEquals("开始日期不能晚于结束日期", reversed.getMessage());

        BizException maxDate = assertThrows(BizException.class, () -> reportController.salesSummary(
                "month", null, LocalDate.MAX));
        assertEquals("日期超出可查询范围", maxDate.getMessage());

        BizException oldDate = assertThrows(BizException.class, () -> reportController.salesSummary(
                "month", LocalDate.of(1800, 1, 1), null));
        assertEquals("日期超出可查询范围", oldDate.getMessage());
    }

    private Invoice issue(TaxEntity entity, String amount) {
        InvoiceRequest request = requestService.create(TestFixtures.req(entity.getId(), "NORMAL"),
                TestFixtures.one(amount, "0.13"));
        requestService.transit(request.getId(), "submit", null);
        requestService.transit(request.getId(), "approve", null);
        return invoiceService.issue(request.getId());
    }
}
