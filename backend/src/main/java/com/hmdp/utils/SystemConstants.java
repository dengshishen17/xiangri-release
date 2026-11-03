package com.hmdp.utils;

public class SystemConstants {
    /**
     * 图片上传目录。默认使用本地 nginx 静态目录，可通过环境变量 IMAGE_UPLOAD_DIR 覆盖。
     * 目录路径不属于敏感信息，仅为方便本地部署而允许外部配置。
     */
    public static final String IMAGE_UPLOAD_DIR =
            System.getenv().getOrDefault("IMAGE_UPLOAD_DIR", "E:\\hm-nginx-1.18.0\\html\\hmdp\\imgs");
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;
}
