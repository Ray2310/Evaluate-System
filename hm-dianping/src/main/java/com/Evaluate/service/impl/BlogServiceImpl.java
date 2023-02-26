package com.Evaluate.service.impl;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.Evaluate.dto.Result;
import com.Evaluate.dto.ScrollResult;
import com.Evaluate.dto.UserDTO;
import com.Evaluate.entity.Blog;
import com.Evaluate.entity.Follow;
import com.Evaluate.entity.User;
import com.Evaluate.mapper.BlogMapper;
import com.Evaluate.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Evaluate.service.IFollowService;
import com.Evaluate.service.IUserService;
import com.Evaluate.utils.SystemConstants;
import com.Evaluate.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    /**
     * 查询当前用户的收件箱
     * @param max 最大偏移量
     * @param offset 与上次查询的最小的一样的元素的个数
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2. 查询收件箱(收件箱的key)
        String key = "feeds:" + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, 0, max, offset, 3);

        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }

        List<Long> ids = new ArrayList<>(typedTuples.size());
        long  minTime = 0 ;
        int count = 1;
        //3. 解析数据（收件箱） blogId ,时间戳 ，minTime offset()
        for(ZSetOperations.TypedTuple<String> tuple :  typedTuples){
            //获取id
            String idStr = tuple.getValue();
            ids.add(Long.valueOf(idStr));
            //获取分数（时间戳）
            long time = tuple.getScore().longValue();
            if(time == minTime){ //时间与最小时间一样
                count++;
            }else {
                minTime = time;
                count = 1;
            }

        }
        //4. 根据id查询blog
        String idStr = StrUtil.join(",",ids);
        List<Blog> blogs =query().eq("id",ids).last("ORDER BY FIELD(id ," + idStr + ")").list();

        for (Blog blog : blogs) {
            //查询blog有关的用户
            queryBlogUser(blog);
            //查询blog是否被点赞
            idBlogLiked(blog);
        }
        //封装并返回
        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setOffset(count);
        result.setMinTime(minTime);
        return Result.ok(result);
    }



    /**
     * 修改新增探店笔记的业务，在保存blog到数据库的同时，推送到粉丝的收件箱
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        //1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        //2.  保存探店博文
        boolean succ = save(blog);
        if(!succ){
            return Result.fail("笔记保存失败！");
        }

        //3. 查询笔记作者的所有粉丝
        //sql语句 ： select * from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        //4. 推送笔记id个所有的粉丝
        for (Follow follow : follows) {
            //获取每一个粉丝
            Long userId = follow.getUserId();
            //推送，收件箱 key粉丝的id
            String key = "feeds:" + userId;
            //推送笔记，按时间戳排序
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }


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
