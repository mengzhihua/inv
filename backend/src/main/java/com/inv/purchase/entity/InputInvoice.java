package com.inv.purchase.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 进项发票 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_input_invoice")
public class InputInvoice extends BaseEntity {
    /** MANUAL/SCAN/IMPORT/EMAIL/API */
    private String source;
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
    private String remark;
    private Long taxEntityId;
    private Long supplierId;
    /** UNVERIFIED/VERIFIED/FAILED */
    private String verifyStatus;
    private String verifyMsg;
    /** PASS / HEADER_MISMATCH / CHECKSUM_FAIL，不落库 */
    @TableField(exist = false)
    private String verifyResult;
    private LocalDateTime verifiedAt;
    /** NORMAL/ABNORMAL/RED_FLUSHED */
    private String status;
    /** PENDING/CHECKED/DEDUCTED/NOT_DEDUCT */
    private String deductStatus;
    private String deductPeriod;
    private String notDeductReason;
    /** UNMATCHED/MATCHED/MISMATCH */
    private String matchStatus;
    private String poNo;
    private String receiptNo;
    private BigDecimal matchDiff;
    /** UNPOSTED/POSTED */
    private String accountStatus;
    private String voucherNo;
}
