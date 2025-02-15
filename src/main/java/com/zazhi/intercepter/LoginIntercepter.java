package com.zazhi.intercepter;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.zazhi.dto.UserDTO;
import com.zazhi.entity.User;
import com.zazhi.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.zazhi.utils.RedisConstants.LOGIN_USER_KEY;
import static com.zazhi.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @author zazhi
 * @date 2025/2/14
 * @description: 登录拦截器
 */
public class LoginIntercepter implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
////        // 1. 获取session中的用户信息
////        HttpSession session = request.getSession();
////        UserDTO user = (UserDTO)session.getAttribute("user");
//
//        // 1. 通过 token 从 redis 中获取用户信息
//        String token = request.getHeader("authorization");
//        if (StrUtil.isBlank(token)) {
//            response.setStatus(401);
//            return false;
//        }
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
//
//        // 2. 判断用户信息是否存在
//        if(userMap.isEmpty()){
//            // 3. 若用户信息不存在, 则拦截下来
//            response.setStatus(401);
//            return false;
//        }
//
//        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//        // 4. 若用户信息存在, 则保存到ThreadLocal中
//        UserHolder.saveUser(user);
//        // 5. 刷新token的过期时间
//        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // upd: 从 ThreadLocal 中获取用户信息判断是否登录
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 6. 请求结束后，清除ThreadLocal中的用户信息
        UserHolder.removeUser();
    }
}
