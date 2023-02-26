package com.Evaluate.service.impl;
import com.Evaluate.dto.Result;
import com.Evaluate.entity.SeckillVoucher;
import com.Evaluate.entity.VoucherOrder;
import com.Evaluate.mapper.VoucherOrderMapper;
import com.Evaluate.service.ISeckillVoucherService;
import com.Evaluate.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Evaluate.utils.RedisIdWorker;
import com.Evaluate.utils.SimpleRedisLock;
import com.Evaluate.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    /**
     * 实现优惠卷秒杀下单
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1. 提交优惠卷id
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2. 查询优惠卷信息
        //3. 判断秒杀是否开启
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            //否 返回异常， 结束
            return Result.fail("秒杀未开始！");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束！");
        }
        //4. 是，判断库存是否充足
            //否 返回异常， 结束
        Integer stock = voucher.getStock();
        if (stock < 1){
            return Result.fail("已经被抢完了！");
        }
        Long UserId = UserHolder.getUser().getId();
        /**
         * 获取互斥锁，只允许一个进入
         */
        //1. 创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + UserId, stringRedisTemplate);
        //2. 获取锁 ( 设置超时时间)
        boolean isLock = lock.tryLock(1200);
        //2.1 获取锁不成功
        if(!isLock){
            return Result.fail("不允许重复下单！");
        }
        //2.2 获取锁成功
        try {
            IVoucherOrderService orderService = (IVoucherOrderService) AopContext.currentProxy();
            return orderService.createVoucherOrder(voucherId);
        } finally {
            //关闭锁
            lock.unLock();
        }
    }

    /**
     * 对于一人一单加安全锁
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId){
        //判断用户是否购买过
        Long UserId = UserHolder.getUser().getId();
        /**
         * 一人一单解决,加锁
         */
        Integer count = query().eq("user_id", UserId).eq("voucher_id", voucherId).count();
        if(count > 0){
            return Result.fail("该用户已经购买过了！");
        }
        //5. 库存充足，扣减库存
        boolean update = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).eq("stock",0) //对乐观锁的判断
                .update();
        if(!update){
            return Result.fail("库存不足！");
        }

        VoucherOrder order = new VoucherOrder();
        //创建用户id，代金卷id ，订单id
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);

        //6. 创建订单  .返回订单信息
        order.setUserId(UserId);
        order.setVoucherId(voucherId);
        save(order);
        return Result.ok(voucherId);
    }
}
