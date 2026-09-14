package com.inv.integration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 电子档案 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_archive")
public class Archive extends BaseEntity {
    private Long invoiceId;
    private String invoiceNo;
    private String invoiceType;
    /** OUT 销项 / IN 进项 */
    private String direction;
    private String issueMonth;
    private String pdfUrl;
    private String ofdUrl;
    private String meta;
}
