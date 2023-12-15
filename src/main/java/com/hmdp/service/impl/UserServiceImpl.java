package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * 用户相关接口
 * @author Ghost
 * @version 1.0
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送短信验证码
     * @param phone
     * @param session
     */
    public Result sendCode(String phone, HttpSession session) {
        // 1、校验手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2、不合法返回错误信息
            return Result.fail("手机号格式不正确！");
        }

        // 3、合法则生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4、在 Redis 中保存验证码
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);


        // 5、发送验证码给客户端
        log.info("发送验证码：{}", code);

        // 发送成功，返回 ok
        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return
     */
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1、校验手机号是否合法
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 不合法返回错误信息
            return Result.fail("手机号格式不正确！");
        }

        // 2、从 Redis 中根据手机号获取验证码进行校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);// 正确的验证码
        String code = loginForm.getCode();// 用户提交的验证码
        if(cacheCode == null || !cacheCode.equals(code)) {
            // 3、验证码错误，返回错误信息
            return Result.fail("验证码不正确！");
        }

        // 4、验证码一致，查询用户
        User user = query().eq("phone", phone).one();

        // 5、判断用户是否存在
        if(user == null) {
            // 6、不存在则向用户表中新增一条数据
            user = createUserWithPhone(phone);
        }

        // 7、将用户信息保存到 Redis 中
        // 7.1 生成 token 作为登录凭证保存到 Redis 中
        String token = UUID.randomUUID().toString(true);

        // 7.2 将 User 对象转为 map 进行存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        // 7.3 将用户信息 User 保存到 Redis 中
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY +token, userMap);

        // 7.4 设置 token 有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY +token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8、返回 token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        // 创建用户，保存到数据库中
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 保存用户
        save(user);
        return user;
    }
}
