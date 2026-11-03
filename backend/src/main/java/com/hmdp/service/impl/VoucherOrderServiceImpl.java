package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.StringReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //订单消息队列名，与 seckill.lua 中 XADD 的目标 stream 保持一致
    private static final String QUEUE_NAME = "stream.order";
    //消费者组名
    private static final String GROUP_NAME = "g1";
    //消费者名
    private static final String CONSUMER_NAME = "c1";

    @PostConstruct
    private void init() {
        //确保订单队列与消费者组存在，避免 Redis 7 下 XREADGROUP 因缺少组而不断抛错
        ensureStreamAndGroup();
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());

    }

    /**
     * 幂等地确保订单 stream 与消费者组存在。
     * 使用 XGROUP CREATE ... MKSTREAM 由 Redis 自动创建 Stream 与消费组，
     * 不再写入任何占位消息，也不清空已有消息与 pending 列表。
     */
    private void ensureStreamAndGroup() {
        try {
            stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
                connection.execute("XGROUP",
                        "CREATE".getBytes(),
                        QUEUE_NAME.getBytes(),
                        GROUP_NAME.getBytes(),
                        "0".getBytes(),
                        "MKSTREAM".getBytes());
                return null;
            });
            log.info("创建订单消费者组成功: {} / {}", QUEUE_NAME, GROUP_NAME);
        } catch (Exception e) {
            //消费组已存在时 Redis 返回 BUSYGROUP，属于正常情况，不阻断消费者启动
            if (containsBusyGroup(e)) {
                log.info("订单消费者组已存在，跳过创建: {} / {}", QUEUE_NAME, GROUP_NAME);
                return;
            }
            //其它 Redis 异常必须抛出，不能被静默吞掉
            log.error("创建订单消费者组失败: {} / {}", QUEUE_NAME, GROUP_NAME, e);
            throw e;
        }
    }

    /**
     * 沿异常链查找 BUSYGROUP 标记。
     */
    private boolean containsBusyGroup(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {

                try {
                    //1.获取队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(QUEUE_NAME, ReadOffset.lastConsumed())
                    );
                    //2.判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //2.1如果获取失败，说明没有消息，继续下一次循环
                        continue;
                    }
                    //解析订单消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //2.2字段不完整的非法消息直接确认丢弃，避免卡住队列
                    if (voucherOrder.getUserId() == null || voucherOrder.getVoucherId() == null) {
                        log.warn("跳过非法订单消息: {}", record.getId());
                        stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, GROUP_NAME, record.getId());
                        continue;
                    }
                    //2.3如果获取成功，说明有消息，可以下单
                    handleVoucherOrder(voucherOrder);
                    //4ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, GROUP_NAME, record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlpendinglist();
                }
            }
        }

        private void handlpendinglist() {
            //单次调用最多处理的 pending 消息数，避免同一条失败消息被无限重试
            int maxRetry = 10;
            int processed = 0;
            while (processed < maxRetry) {

                try {
                    //1.获取pendinglist队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty().count(1).count(1),
                            StreamOffset.create(QUEUE_NAME, ReadOffset.from("0"))
                    );
                    //2.判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //2.1如果获取失败，说明pendinglist没有消息，结束次循环
                        break;
                    }
                    //解析订单消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //字段不完整的非法消息直接确认丢弃，避免卡住队列
                    if (voucherOrder.getUserId() == null || voucherOrder.getVoucherId() == null) {
                        log.warn("跳过非法pending订单消息: {}", record.getId());
                        stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, GROUP_NAME, record.getId());
                        processed++;
                        continue;
                    }
                    //2.2如果获取成功，说明有消息，可以下单
                    handleVoucherOrder(voucherOrder);
                    //4ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, GROUP_NAME, record.getId());
                    processed++;
                } catch (Exception e) {
                    log.error("处理pending-list订单异常", e);
                    processed++;
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
//    private class VoucherOrderHandler implements Runnable{
//        @Override
//        public void run() {
//            while (true){
//                //1.获取队列中的订单信息
//                try {
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    //2.创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (InterruptedException e) {
//                    log.error("处理订单异常");
//                }
//            }
//        }
//    }


    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1.获取用户
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //3.获取锁
        boolean isLock = lock.tryLock();
        //4.判断锁是否成功
        if (!isLock) {
            //获取锁失败
            log.error("一人只允许下一单");
            return;
        }
        try {
            //消费者运行在独立线程池线程中，不存在 AOP 调用上下文，
            //AopContext.currentProxy() 在此线程必然抛 IllegalStateException。
            //这里改用 TransactionTemplate 显式开启事务，
            //既保证"一人一单 + 扣库存 + 写订单"的原子性，也不依赖代理对象暴露。
            transactionTemplate.execute(status -> {
                createVoucherOrderByVoucherOrder(voucherOrder);
                return null;
            });
        } finally {
            //释放锁
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //获取订单id
        long orderId = redisIdWorker.nextId("order");
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(), String.valueOf(orderId)
        );
        //2.判断结果是否为零
        int r = result.intValue();
        if (r != 0) {
            //2.1不为零代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
//        //2.2为零代表有购买资格
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //2.3订单id
//
//        voucherOrder.setId(orderId);
//        //2.4用户id
//        voucherOrder.setUserId(userId);
//        //2.5代金券id
//        voucherOrder.setVoucherId(voucherId);
//        //2.6放入阻塞队列
//        orderTasks.add(voucherOrder);
        //3获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //4.返回订单id
        return Result.ok(orderId);

    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //获取用户
//        Long userId=UserHolder.getUser().getId();
//        //1.执行lua脚本
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(),
//                userId.toString()
//        );
//        //2.判断结果是否为零
//        int r = result.intValue();
//        if (r != 0) {
//            //2.1不为零代表没有购买资格
//            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
//        }
//        //2.2为零代表有购买资格
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //2.3订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //2.4用户id
//        voucherOrder.setUserId(userId);
//        //2.5代金券id
//        voucherOrder.setVoucherId(voucherId);
//        //2.6放入阻塞队列
//        orderTasks.add(voucherOrder);
//        //4.返回订单id
//        return Result.ok(orderId);
//
//    }


    @Override
    @Transactional
    public void createVoucherOrder(Long voucherId) {
        // 这是接口定义的方法，但我们通过队列异步处理，所以这个方法暂时不执行任何操作
        // 实际的订单处理通过队列异步完成
    }

    @Transactional
    public void createVoucherOrderByVoucherOrder(VoucherOrder voucherOrder) {
        //6.一人一单
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        //6.1查询订单
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //6.2判断是否存在
        if (count > 0) {
            log.error("用户已经购买过了");
            return;
        }
        //5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success) {
            //扣减失败
            log.error("库存不足");
            return;
        }

        //6.创建订单
        save(voucherOrder);

    }
}
