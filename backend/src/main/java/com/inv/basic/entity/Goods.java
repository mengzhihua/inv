package com.inv.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 商品/服务 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_goods")
public class Goods extends BaseEntity {
    private String code;
    private String name;
    /** 税收分类编码（19 位数字） */
    private String taxCategoryCode;
    private String taxCategoryName;
    private String spec;
    private String unit;
    private BigDecimal price;
    private BigDecimal taxRate;
    /** NONE/FREE/ZERO */
    private String preferentialPolicy;
    private Integer status;
}
