package com.zazhi.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zazhi.dto.Result;
import com.zazhi.dto.UserDTO;
import com.zazhi.entity.Blog;
import com.zazhi.entity.User;
import com.zazhi.mapper.BlogMapper;
import com.zazhi.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zazhi.service.IUserService;
import com.zazhi.utils.SystemConstants;
import com.zazhi.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.zazhi.utils.RedisConstants.BLOG_LIKED_KEY;

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

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());

            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    public void isBlogLiked(Blog blog){
        Long userId = UserHolder.getUser().getId();
        Double score = stringRedisTemplate.opsForZSet().score("blog:liked:" + blog.getId(), String.valueOf(userId));
        blog.setIsLike(score != null);
    }


    @Override
    public Result queryById(Long id) {
        // 1. 查询blog
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("博文不存在");
        }
        // 2. 查询blog有关的用户
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());

        // 3. 查询是否点赞过
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1. 判断是否点赞过
        Long userId = UserHolder.getUser().getId();
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, String.valueOf(userId));
        if(score == null){
            // 2. 若未点赞过，点赞
            // 2.1 数据库点赞数 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 2.2 保存用户到 Redis 的 Set 集合
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, String.valueOf(userId), System.currentTimeMillis());
            }
        }else{
            // 3. 若已点赞过，取消点赞
            // 3.1 数据库点赞数 - 1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 3.2 从 Redis 的 Set 集合中删除用户
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, String.valueOf(userId));
            }
        }


        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 1. 查询 Redis 的 Set 集合
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        if(top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        // 2. 查询用户信息 TODO 有点问题
        List<Long> collect = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDtos = userService.listByIds(collect)
                .stream()
                .map(user ->
                        BeanUtil.copyProperties(user, UserDTO.class)
                ).collect(Collectors.toList());
        return Result.ok(userDtos);
    }
}
