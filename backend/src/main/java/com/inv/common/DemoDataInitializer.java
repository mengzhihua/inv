package com.inv.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 依赖当前日期的演示数据（属期、费用票），data.sql 只保留纯标准 SQL。
 * 幂等：目标行不存在才插入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        String cur = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String prev = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));

        insertPeriod("HQ", cur, "OPEN");
        insertPeriod("HQ", prev, "CLOSED");
        insertPeriod("SZ", cur, "OPEN");

        // 费用票演示行：正常票 + 一张超期超限风险票
        String expNo = "EXP" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "001";
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inv_expense_invoice WHERE employee_no = 'E1001'", Integer.class);
        if (cnt == null || cnt == 0) {
            jdbc.update("INSERT INTO inv_expense_invoice (employee_name, employee_no, department, upload_time, invoice_type, invoice_no, issue_date,"
                            + " seller_name, seller_tax_no, buyer_name, buyer_tax_no, total_amount, total_tax, total_with_tax, status, created_at, updated_at)"
                            + " VALUES (?,?,?,CURRENT_TIMESTAMP,'OTHER',?,CURRENT_DATE,?,?,?,?,?,?,?,'UPLOADED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    "陈出差", "E1001", "销售部", expNo,
                    "中国铁路上海局", "91310000132200001X", "云途科技股份有限公司", "91310000780000001A",
                    553.00, 0, 553.00);
        }
        cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inv_expense_invoice WHERE employee_no = 'E1002'", Integer.class);
        if (cnt == null || cnt == 0) {
            jdbc.update("INSERT INTO inv_expense_invoice (employee_name, employee_no, department, upload_time, invoice_type, invoice_no, issue_date,"
                            + " seller_name, seller_tax_no, buyer_name, buyer_tax_no, total_amount, total_tax, total_with_tax, status, risk_items, created_at, updated_at)"
                            + " VALUES (?,?,?,CURRENT_TIMESTAMP,'NORMAL','EXP-OLD-001',?,?,?,?,?,?,?,?,'RISK','[\"EXPIRED\",\"AMOUNT_LIMIT\"]',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    "刘市场", "E1002", "市场部", LocalDate.now().minusDays(200),
                    "某会议会展公司", "91500000MA5K00008X", "云途科技股份有限公司", "91310000780000001A",
                    60000.00, 3600.00, 63600.00);
        }
        log.info("演示数据初始化完成：属期 {} / {}，费用票 E1001/E1002", cur, prev);
    }

    private void insertPeriod(String entityCode, String period, String status) {
        jdbc.update("INSERT INTO inv_tax_period (tax_entity_id, period, status, created_at, updated_at)"
                        + " SELECT e.id, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM inv_tax_entity e"
                        + " WHERE e.code = ? AND NOT EXISTS (SELECT 1 FROM inv_tax_period p WHERE p.tax_entity_id = e.id AND p.period = ?)",
                period, status, entityCode, period);
    }
}
