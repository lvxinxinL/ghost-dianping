package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * 用户相关接口
 * @author Ghost
 * @version 1.0
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

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

        // 4、在 session 中保存验证码
        session.setAttribute("code", code);

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

        // 2、校验验证码
        Object cacheCode = session.getAttribute("code");// 正确的验证码
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

        // 7、将用户信息保存到 session
        session.setAttribute("user", user);

        // 返回登录成功
        return Result.ok();
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
