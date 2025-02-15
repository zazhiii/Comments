package com.zazhi.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zazhi.dto.LoginFormDTO;
import com.zazhi.dto.Result;
import com.zazhi.dto.UserDTO;
import com.zazhi.entity.User;
import com.zazhi.mapper.UserMapper;
import com.zazhi.service.IUserService;
import com.zazhi.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.zazhi.utils.RedisConstants.*;
import static com.zazhi.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     * @param phone 手机号
     * @param session 会话
     * @return 发送结果
     */
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){;
            return Result.fail("手机号格式不正确");
        }
        // 2. 生成验证码
        String code = RandomUtil.randomNumbers(6);

//        // 3. 保存验证码到session
//        session.setAttribute("code", code);

        // 3. 保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 4. 发送验证码 TODO：发送验证码
        log.debug("向手机号{}发送验证码：{}", phone, code);

        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确");
        }

//        // 2. 校验验证码 从session中获取验证码与用户输入的验证码进行比对
//        String cacheCode = session.getAttribute("code").toString();

        // 2. 校验验证码 从redis中获取验证码与用户输入的验证码进行比对
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.equals(code)){
            return Result.fail("验证码错误");
        }
        // 3. 根据手机号查询用户, 判断用户是否存在。
        User user = query().eq("phone", phone).one();
        if(user == null){
            // 若用户不存在，则注册用户
            user = new User();
            user.setPhone(phone);
            user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            save(user);
        }

//        // 4. 保存用户信息到session
//        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class); // 这个方法是hutool提供的，可以将一个对象的属性拷贝到另一个对象中
//        session.setAttribute("user", userDTO);

        // 4.1 生成token
        String token = UUID.randomUUID().toString();
        // 4.2 bean 转为 hash
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);

        // fix: 因为用的StringRedisTemplate，所以要用String类型的map
        Map<String, String> userMap = new HashMap<>();
        userMap.put("id", userDTO.getId().toString());
        userMap.put("nickName", userDTO.getNickName());
        userMap.put("icon", userDTO.getIcon());

        // 4.3 保存到redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        // 4.4 设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.SECONDS);
        // 5. 返回token
        return Result.ok(token);
    }
}
