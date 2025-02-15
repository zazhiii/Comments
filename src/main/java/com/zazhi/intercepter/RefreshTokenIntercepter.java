package com.zazhi.intercepter;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.zazhi.dto.UserDTO;
import com.zazhi.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.zazhi.utils.RedisConstants.LOGIN_USER_KEY;
import static com.zazhi.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @author zazhi
 * @date 2025/2/15
 * @description: 刷新 token
 */
public class RefreshTokenIntercepter implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenIntercepter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 1. 获取session中的用户信息
//        HttpSession session = request.getSession();
//        UserDTO user = (UserDTO)session.getAttribute("user");

        // 1. 通过 token 从 redis 中获取用户信息
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
//            response.setStatus(401);
//            return false;
            return true; // upd: 放行到 loginIntercepter
        }
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        // 2. 判断用户信息是否存在
        if(userMap.isEmpty()){
//            response.setStatus(401);
//            return false;
            return true; // upd: 放行到 loginInter
        }

        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 4. 若用户信息存在, 则保存到ThreadLocal中
        UserHolder.saveUser(user);
        // 5. 刷新token的过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 6. 请求结束后，清除ThreadLocal中的用户信息
        UserHolder.removeUser();
    }
}
