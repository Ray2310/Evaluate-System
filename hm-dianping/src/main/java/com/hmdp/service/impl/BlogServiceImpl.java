package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取页面的所有用户
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        //todo 查询用户id
        Page<Blog> page = query().orderByDesc("liked").page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        //获取当前页的数据
        List<Blog> records = page.getRecords();
        //查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.idBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 点赞用户列表查询
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        //1. 查询点赞前 top5的用户
        String key = "blog:liked" + id;

        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析出用户id
        List<Long> userIds = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        /**
         * 针对点赞用户排序的问题的改进
         */
        String idStr = StrUtil.join(",", userIds);
        //根据用户id查询用户
        //List<UserDTO> userDTOs = userService.listByIds(userIds).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.query().in("id", userIds).last("ORDER BY FIELD(id," + idStr + ")").list().
                stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        //返回
        return Result.ok(userDTOS);
    }
    /**
     * 实现根据博客查询用户的id
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记不存在！");
        }
        //todo 查询blog有关的用户
        queryBlogUser(blog);
        //todo 查询blog是否被点过赞 ，封装成方法
        idBlogLiked(blog);
        return Result.ok(blog);
    }
    @Override
    public Result likeBlog(Long id) {
        //1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2. 判断当前用户是否点赞
        String key = "blog:liked" + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        //3.如果未点赞，可以实现点赞
        if(score == null){
            //3.1 数据库点赞数 + 1
            boolean isSuccess = update()
                    .setSql("liked = liked + 1").eq("id", id).update();
            //3.2 保存用户到Redis的set集合中
            if(isSuccess){
                //key value score(按照时间戳排序)
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }
        //4. 如果已经点过赞，点击的话就会 取消点赞
        else {
            //4.1数据库点赞数 - 1
            boolean isSuccess = update()
                    .setSql("liked = liked - 1").eq("id", id).update();

            //4.2 将用户从点赞列表中移除
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }


    //todo 封装的根据博客查询博主信息
    public void queryBlogUser(Blog blog){
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
    //todo 封装判断是否被点过赞的功能
    private void idBlogLiked(Blog blog){
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return; //用户未登录无需查询是否点赞
        }
        Long userId =user.getId();
        String key = "blog:liked" + blog.getId();
        Double isMember = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(isMember!= null);
    }

}
