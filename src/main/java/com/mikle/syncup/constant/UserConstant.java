package com.mikle.syncup.constant;

/**
 * 用户常量
 */
public interface UserConstant {

    /**
     * Token request header name.
     */
    String TOKEN_NAME = "Authorization";

    /**
     * Token request header prefix.
     */
    String TOKEN_PREFIX = "Bearer";

    //  ------- 权限 --------

    /**
     * 默认权限
     */
    int DEFAULT_ROLE = 0;

    /**
     * 管理员权限
     */
    int ADMIN_ROLE = 1;

}
