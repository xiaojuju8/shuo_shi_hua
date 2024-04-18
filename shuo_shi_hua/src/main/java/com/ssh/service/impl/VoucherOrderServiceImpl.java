package com.ssh.service.impl;

import com.ssh.dto.Result;
import com.ssh.entity.SeckillVoucher;
import com.ssh.entity.VoucherOrder;
import com.ssh.mapper.VoucherOrderMapper;
import com.ssh.service.ISeckillVoucherService;
import com.ssh.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ssh.utils.RedisIdWorker;
import com.ssh.utils.SimpleRedisLock;
import com.ssh.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())){
            //尚未开始
            return Result.fail("秒杀尚未开始!");
        }
        //3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())){
            //尚未开始
            return Result.fail("秒杀已经结束!");
        }
        //4.判断库存是否充足
        if (voucher.getStock()<1){
            //库存不足
            return Result.fail("库存不足！");
        }

        Long userId = UserHolder.getUser().getId();
        // 通过userId控制锁的粒度，只有相同用户才会加锁
        // synchronized是java内置的一个线程同步关键字，可以卸载需要同步的对象、方法或者特定的代码块中
        // intern()方法是将字符串放入常量池中，这样相同的字符串就会指向同一个对象，从而实现锁的粒度控制
        //synchronized (userId.toString().intern()) {
            // 如果直接使用this调用方法，调用的是非代理对象，但是事务是靠代理对象生效的，所以我们要拿到代理对象，走代理对象的方法，才能实现事务控制
            // 通过AopContext.currentProxy()获取代理对象，从而实现事务控制

        //创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        //获取锁对象
        boolean isLock = lock.tryLock(1200);
        //加锁失败
        if (!isLock){
            return Result.fail("之前下单逻辑还在处理/不允许重复下单");
        }
        // 这里就是为了调用createVoucherOrder方法，但是要考虑到事务的问题，所以要通过代理对象来调用
        try {
            //获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }
       //}
    }

    public Result createVoucherOrder(Long voucherId) {
        //5.一人一单
        Long userId = UserHolder.getUser().getId();
        //5.1查询订单
        int count = query().eq("user_id",userId).eq("voucher_id", voucherId).count();
        //5.2判断是否存在
        if (count>0){
            //用户已经购买过了
            return Result.fail("不好意思，只能购买一次");
        }
        //6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1").eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if (!success){
            //扣减失败
            return Result.fail("库存不足!");
        }
        //7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //7.1订单id
        long orderId = RedisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //7.2用户id
        voucherOrder.setUserId(userId);
        //7.3代金卷id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //8.返回订单id
        return Result.ok(orderId);
    }
}
