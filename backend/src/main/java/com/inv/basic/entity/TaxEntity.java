package com.inv.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 纳税主体（本企业开票方） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_tax_entity")
public class TaxEntity extends BaseEntity {
    private String code;
    private String name;
    /** 纳税人识别号：15/17/18/20 位 */
    private String taxNo;
    private String address;
    private String phone;
    private String bankName;
    private String bankAccount;
    /** GENERAL 一般纳税人 / SMALL 小规模 */
    private String taxpayerType;
    private String drawer;
    private String payee;
    private String reviewer;
    private Integer status;
}
