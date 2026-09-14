package com.inv.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 已开具发票 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_invoice")
public class Invoice extends BaseEntity {
    private String invoiceCode;
    private String invoiceNo;
    private String invoiceType;
    private Long taxEntityId;
    private String buyerName;
    private String buyerTaxNo;
    private String buyerAddressPhone;
    private String buyerBank;
    private String sellerName;
    private String sellerTaxNo;
    private String sellerAddressPhone;
    private String sellerBank;
    private LocalDate issueDate;
    private BigDecimal totalAmount;
    private BigDecimal totalTax;
    private BigDecimal totalWithTax;
    private String checkCode;
    /** ISSUED/CANCELLED/RED_FLUSHED/RED */
    private String status;
    private Long redOfInvoiceId;
    private Long requestId;
    private Long redInfoId;
    private String pdfUrl;
    private String machineNo;
    private String drawer;
    private String payee;
    private String reviewer;
    private String deliveryStatus;
    /** 累计已红冲金额/税额（正数） */
    private BigDecimal redAmount;
    private BigDecimal redTax;
}
