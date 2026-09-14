package com.inv.sales.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 红字信息表 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_red_info")
public class RedInfo extends BaseEntity {
    private String redInfoNo;
    private Long invoiceId;
    /** BUYER_REJECT/SELLER_ERROR/SERVICE_STOP/RETURN */
    private String reason;
    private BigDecimal totalAmount;
    private BigDecimal totalTax;
    private BigDecimal totalWithTax;
    /** DRAFT/CONFIRMED/USED */
    private String status;
}
