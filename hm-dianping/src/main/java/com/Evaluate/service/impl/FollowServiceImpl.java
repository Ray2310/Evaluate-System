package com.Evaluate.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.Evaluate.dto.Result;
import com.Evaluate.dto.UserDTO;
import com.Evaluate.entity.Follow;
import com.Evaluate.mapper.FollowMapper;
import com.Evaluate.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Evaluate.service.IUserService;
import com.Evaluate.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService service;

    //todo 关注还是取关
    @Override
    public Result follow(Long followUserId, boolean isFollow) {
        //1. 获取登录的用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //1。 判断到底是关注还是取关
        /**
         * 当需要关注时，加入redis
         */
        if(isFollow){
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSecc = save(follow);
            if(isSecc){
                //把当前用户的id，放入redis的set集合中
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }
        //取消关注
        else{
            boolean isSucc = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSucc){
                //把关注的用户id从redis中移除
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 查询用户是否关注
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);    //如果大于0就代表关注
    }

    /**
     * 查询当前用户和目标用户的共同关注
     * @param id 目标用户的id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + userId;//当前用户
        String key2 = "follows:" + id; //目标用户的key
        //2. 求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        //没有交集的情况
        if(intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //有交集
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = service.listByIds(ids).stream().map(user ->
                BeanUtil.copyProperties(user, UserDTO.class)
        ).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
