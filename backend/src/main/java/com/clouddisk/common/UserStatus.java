package com.clouddisk.common;

public final class UserStatus {

    /** 已禁用 */
    public static final int DISABLED = 0;
    /** 正常 */
    public static final int ACTIVE = 1;
    /** 注册待管理员审核 */
    public static final int PENDING = 2;

    /** 新用户默认存储配额：200 GB */
    public static final long DEFAULT_QUOTA_BYTES = 200L * 1024 * 1024 * 1024;

    private UserStatus() {
    }
}
