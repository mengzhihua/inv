package com.inv.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.inv.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户。role: ADMIN(全部) / FINANCE(全部业务) / OPERATOR(录入/申请/上传, 不可审核开票/勾选/关账) / VIEWER(只读)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_user")
public class User extends BaseEntity {
    public static final String ADMIN = "ADMIN";
    public static final String FINANCE = "FINANCE";
    public static final String OPERATOR = "OPERATOR";
    public static final String VIEWER = "VIEWER";

    private String username;
    /** 写入时可携带明文密码，响应中永不输出 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String realName;
    private String role;
    private Integer status;
    private LocalDateTime lastLoginAt;
}
