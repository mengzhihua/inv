package com.inv.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 往来单位 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_partner")
public class Partner extends BaseEntity {
    /** CUSTOMER/SUPPLIER/BOTH */
    private String type;
    private String name;
    private String taxNo;
    private String address;
    private String phone;
    private String bankName;
    private String bankAccount;
    private String email;
    private String mobile;
    private Integer status;
}
