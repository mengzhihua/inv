package com.inv.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 费用发票/报销 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_expense_invoice")
public class ExpenseInvoice extends BaseEntity {
    private String employeeName;
    private String employeeNo;
    private String department;
    private LocalDateTime uploadTime;
    private String invoiceType;
    private String invoiceCode;
    private String invoiceNo;
    private LocalDate issueDate;
    private String sellerName;
    private String sellerTaxNo;
    private String buyerName;
    private String buyerTaxNo;
    private BigDecimal totalAmount;
    private BigDecimal totalTax;
    private BigDecimal totalWithTax;
    private String checkCode;
    private String reimburseNo;
    /** UPLOADED/COMPLIANT/RISK/REIMBURSED/REJECTED */
    private String status;
    private String riskItems;
    private String rejectReason;
}
