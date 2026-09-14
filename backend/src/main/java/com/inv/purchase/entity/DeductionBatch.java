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
@TableName("inv_deduction_batch")
public class DeductionBatch extends BaseEntity {
    private Long taxEntityId;
    private String period;
    private String batchNo;
    private Integer invoiceCount;
    private BigDecimal totalAmount;
    private BigDecimal totalTax;
}
