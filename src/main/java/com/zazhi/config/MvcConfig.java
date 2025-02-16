package com.zazhi.config;

import com.zazhi.intercepter.LoginIntercepter;
import com.zazhi.intercepter.RefreshTokenIntercepter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author zazhi
 * @date 2025/2/14
 * @description: MVC配置类
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(new LoginIntercepter())
                .excludePathPatterns(
                        "/shop/**",
                        "/shop-type/**",
                        "voucher/**",
                        "upload/**",
                        "blog/hot",
                        "/user/login",
                        "/user/code",
                        // knif4j
                        "/doc.html",
                        "/v3/**",
                        "/error"
                ).order(1);

        // 刷新 Token
        registry.addInterceptor(new RefreshTokenIntercepter(stringRedisTemplate))
                .addPathPatterns("/**").order(0);
    }
}
