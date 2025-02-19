package com.zazhi.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zazhi.dto.Result;
import com.zazhi.entity.Blog;
import com.zazhi.entity.User;
import com.zazhi.mapper.BlogMapper;
import com.zazhi.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zazhi.service.IUserService;
import com.zazhi.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

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
        });
        return Result.ok(records);
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
        return Result.ok(blog);
    }
}
