package com.inv.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 开票申请 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_invoice_request")
public class InvoiceRequest extends BaseEntity {
    private String requestNo;
    private Long taxEntityId;
    private Long customerId;
    /** SPECIAL/NORMAL/E_NORMAL/E_SPECIAL/ALL_ELECTRIC */
    private String invoiceType;
    private String buyerName;
    private String buyerTaxNo;
    private String buyerAddressPhone;
    private String buyerBank;
    private String remark;
    /** MANUAL/OMS/BMS/IMPORT */
    private String source;
    private String extRef;
    private BigDecimal totalAmount;
    private BigDecimal totalTax;
    private BigDecimal totalWithTax;
    /** DRAFT/SUBMITTED/APPROVED/ISSUED/REJECTED/CANCELLED */
    private String status;
    private String rejectReason;
    private Long invoiceId;
    /** 明细超 8 行的专票需附销货清单 */
    private Integer withList;
}
