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
@TableName("inv_delivery_log")
public class DeliveryLog extends BaseEntity {
    private Long invoiceId;
    /** EMAIL/SMS */
    private String channel;
    private String target;
    private String status;
}
