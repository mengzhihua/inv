package com.inv.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_red_info_line")
public class RedInfoLine extends BaseEntity {
    private Long redInfoId;
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
