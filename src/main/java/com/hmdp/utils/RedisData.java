package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 需要增加过期时间的缓存数据
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
