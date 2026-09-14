package com.inv.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 发票号段库存 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_invoice_stock")
public class InvoiceStock extends BaseEntity {
    private Long taxEntityId;
    private String invoiceType;
    private String invoiceCode;
    private String startNo;
    private String endNo;
    private String currentNo;
    private Integer remaining;
    private Integer status;
}
