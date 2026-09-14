package com.inv.system.auth;

import com.inv.system.entity.User;

/**
 * 角色访问策略（按 HTTP 方法 + 路径判断）：
 * <ul>
 *   <li>任何登录用户可读（GET）业务数据；用户目录与操作审计(/api/system/**)仅 ADMIN 可读</li>
 *   <li>VIEWER 不可写</li>
 *   <li>OPERATOR 可录入/申请/上传，不可审核开票/勾选/关账/红冲/作废，不可维护基础数据与用户</li>
 *   <li>FINANCE 可做全部业务操作，不可维护用户(/api/system/**)</li>
 *   <li>ADMIN 无限制</li>
 * </ul>
 * /api/auth/** 属于登录用户自助操作（改密、登出），所有角色均可。
 */
public final class AccessPolicy {
    private AccessPolicy() {
    }

    /** OPERATOR 禁止的写操作路径片段：审核/开票/作废/红冲确认/勾选抵扣/关账 */
    private static final String[] OPERATOR_FORBIDDEN = {
            "/approve", "/issue", "/red-flush", "/red-info/confirm",
            "/check", "/deduct", "/not-deduct", "/period/close", "/period/open", "/post"
    };

    /** 发票作废（区别于申请单撤销，OPERATOR 可撤销自己录入的申请） */
    private static boolean invoiceCancel(String path) {
        return path.contains("/invoice/") && path.contains("/cancel");
    }

    public static boolean allows(String role, String method, String path) {
        if (User.ADMIN.equals(role)) {
            return true;
        }
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        if (path.startsWith("/api/system/")) {
            return false;
        }
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return true;
        }
        if (User.FINANCE.equals(role)) {
            return true;
        }
        if (User.OPERATOR.equals(role)) {
            if (path.startsWith("/api/basic/") || invoiceCancel(path)) {
                return false;
            }
            for (String frag : OPERATOR_FORBIDDEN) {
                if (path.contains(frag)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
