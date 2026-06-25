package com.clouddisk.common;

public final class UserStatus {

    /** 已禁用 */
    public static final int DISABLED = 0;
    /** 正常 */
    public static final int ACTIVE = 1;
    /** 注册待管理员审核 */
    public static final int PENDING = 2;

    /** 普通用户默认存储配额：3 GB */
    public static final long DEFAULT_USER_QUOTA_BYTES = 3L * 1024 * 1024 * 1024;
    /** 管理员默认存储配额：5 GB */
    public static final long DEFAULT_ADMIN_QUOTA_BYTES = 5L * 1024 * 1024 * 1024;
    /** 普通用户申请扩容目标：500 GB */
    public static final long USER_APPLY_QUOTA_BYTES = 500L * 1024 * 1024 * 1024;

    private UserStatus() {
    }
}
