package com.inv.purchase.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_input_invoice_line")
public class InputInvoiceLine extends BaseEntity {
    private Long inputInvoiceId;
    private String goodsName;
    private String taxCategoryCode;
    private String spec;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
}
