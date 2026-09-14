package com.inv.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_tax_entity_limit")
public class TaxEntityLimit extends BaseEntity {
    private Long taxEntityId;
    private String invoiceType;
    private BigDecimal maxAmount;
}
