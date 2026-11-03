package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,Long time,TimeUnit unit) {
        String key = keyPrefix+ id;
        //1.从redis查询商铺缓存
        String Json = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        //2.判断是否存在
        if (StrUtil.isNotBlank(Json)) {
            //3.存在，返回
            return JSONUtil.toBean(Json, type);
        }
        //判断是否为空值
        if (Json != null){
            //返回错误信息
            return null;
        }

        //4.不存在，查询数据库
        R r = dbFallback.apply(id);
        //5.不存在，返回错误
        if (r==null) {
            //将空值写入Redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.存在，写入redis
        this.set(key,r,time,unit);
        //7.返回
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R,ID> R queryWithLogicalExpire(String keyPrefix ,ID id ,Class<R> type,Function<ID,R> dbFallback){
        String key = keyPrefix + id;
        //1.从redis查询商铺缓存
        String Json = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        //2.判断是否存在
        if (StrUtil.isBlank(Json)) {
            //3.存在，返回
            return null;
        }

        //4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否为过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //5.1未过期直接返回店铺信息
            return r;
        }
        //5.2已过期，需要缓存重建
        //6.重建缓存
        //6.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLocked = tryLock(lockKey);
        //6.2判断是否获取成功
        if (isLocked) {
            //6.3成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                //重建缓存
                try {
                    //查数据库
                    R r1 = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,r1,CACHE_SHOP_TTL,TimeUnit.MINUTES);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //.释放锁
                    unLock(lockKey);
                }
            });
        }
        //6.4返回过期店铺信
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }



}
