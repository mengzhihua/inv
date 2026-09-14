package com.inv.tax.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_tax_period")
public class TaxPeriod extends BaseEntity {
    private Long taxEntityId;
    private String period;
    /** OPEN/CLOSED */
    private String status;
}
