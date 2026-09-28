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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void exportSummaryRespectsDateRange() {
        TaxEntity entity = fx.newEntity("T-REPORT-EXP", "91310000TREPORTEXP1");
        fx.stock(entity.getId(), "NORMAL", "999000000032", "60000001", "60000100");
        Invoice inRange = issue(entity, "100.00", "区间导出客户A");
        inRange.setIssueDate(LocalDate.now().withDayOfMonth(15));
        invoiceMapper.updateById(inRange);
        Invoice outRange = issue(entity, "200.00", "区间导出客户B");
        outRange.setIssueDate(LocalDate.now().minusMonths(3).withDayOfMonth(10));
        invoiceMapper.updateById(outRange);

        LocalDate from = LocalDate.now().withDayOfMonth(1);
        LocalDate to = LocalDate.now().withDayOfMonth(28);
        byte[] csv = reportController.exportSummary("customer", from, to).getBody();
        assertNotNull(csv);
        String body = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains(inRange.getBuyerName()));
        assertTrue(!body.contains(outRange.getBuyerName()));
    }

    @Test
    void customerRankRespectsDateRange() {
        TaxEntity entity = fx.newEntity("T-REPORT-RANK", "91310000TREPORTRANK1");
        fx.stock(entity.getId(), "NORMAL", "999000000033", "70000001", "70000100");
        Invoice inRange = issue(entity, "100.00", "区间排名客户A");
        inRange.setIssueDate(LocalDate.now().withDayOfMonth(15));
        invoiceMapper.updateById(inRange);
        Invoice outRange = issue(entity, "200.00", "区间排名客户B");
        outRange.setIssueDate(LocalDate.now().minusMonths(3).withDayOfMonth(10));
        invoiceMapper.updateById(outRange);

        LocalDate from = LocalDate.now().withDayOfMonth(1);
        LocalDate to = LocalDate.now().withDayOfMonth(28);
        List<Map<String, Object>> rows = reportController.customerRank(10, from, to).getData();
        assertNotNull(rows);
        boolean sawIn = false;
        for (Map<String, Object> row : rows) {
            Object dim = row.get("dim") == null ? row.get("DIM") : row.get("dim");
            assertTrue(!String.valueOf(dim).equals(outRange.getBuyerName()));
            if (String.valueOf(dim).equals(inRange.getBuyerName())) {
                sawIn = true;
            }
        }
        assertTrue(sawIn);
    }

    private Invoice issue(TaxEntity entity, String amount) {
        return issue(entity, amount, "测试买方公司");
    }

    private Invoice issue(TaxEntity entity, String amount, String buyerName) {
        InvoiceRequest req = TestFixtures.req(entity.getId(), "NORMAL");
        req.setBuyerName(buyerName);
        InvoiceRequest request = requestService.create(req,
                TestFixtures.one(amount, "0.13"));
        requestService.transit(request.getId(), "submit", null);
        requestService.transit(request.getId(), "approve", null);
        return invoiceService.issue(request.getId());
    }
}
