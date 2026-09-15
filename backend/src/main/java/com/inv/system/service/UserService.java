package com.inv.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inv.common.BizException;
import com.inv.system.auth.PasswordHasher;
import com.inv.system.auth.TokenService;
import com.inv.system.entity.User;
import com.inv.system.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements ApplicationRunner {
    private static final List<String> ROLES = Arrays.asList(User.ADMIN, User.FINANCE, User.OPERATOR, User.VIEWER);
    private static final int MAX_LOGIN_FAILURE_ENTRIES = 10000;

    private final UserMapper userMapper;
    private final TokenService tokenService;

    @Value("${inv.auth.admin-password:}")
    private String initialAdminPassword;

    @Value("${inv.login.max-fail:5}")
    private int loginMaxFail;

    @Value("${inv.login.lock-minutes:15}")
    private int loginLockMinutes;

    /** 登录失败计数：同一用户名连续失败 maxFail 次锁定 lockMinutes 分钟（内存级，重启清零） */
    private final ConcurrentHashMap<String, long[]> loginFail = new ConcurrentHashMap<>();

    /**
     * 首次启动无任何用户时创建 admin 账号。口令来自 inv.auth.admin-password / INV_ADMIN_PASSWORD；
     * 未配置时随机生成一次性初始口令并输出到启动日志，避免公开的固定默认口令。
     */
    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        String password = initialAdminPassword;
        boolean generated = password == null || password.isEmpty();
        if (generated) {
            byte[] buf = new byte[12];
            new SecureRandom().nextBytes(buf);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        }
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(PasswordHasher.hash(password));
        admin.setRealName("系统管理员");
        admin.setRole(User.ADMIN);
        admin.setStatus(1);
        userMapper.insert(admin);
        if (generated) {
            log.warn("未配置 INV_ADMIN_PASSWORD，已为 admin 生成一次性初始口令: {}  请登录后立即修改", password);
        } else {
            log.info("已初始化管理员账号 admin");
        }
    }

    @Data
    public static class LoginResult {
        private String token;
        private User user;
    }

    @Transactional
    public LoginResult login(String username, String password) {
        String key = username == null ? "" : username;
        long now = System.currentTimeMillis();
        cleanupLoginFailures(now);
        if (loginFail.size() >= MAX_LOGIN_FAILURE_ENTRIES && !loginFail.containsKey(key)) {
            trimLoginFailures(now);
        }
        long[] rec = loginFail.get(key);
        if (rec != null && rec[0] >= loginMaxFail && now < rec[1]) {
            throw new BizException("账号已锁定，请稍后再试");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !PasswordHasher.verify(password, user.getPassword())) {
            long lockMs = loginLockMinutes * 60_000L;
            synchronized (loginFail) {
                if (loginFail.size() < MAX_LOGIN_FAILURE_ENTRIES || loginFail.containsKey(key)) {
                    loginFail.compute(key, (k, v) -> {
                        if (v == null || v.length < 3 || (v[2] > 0 && now - v[2] >= lockMs)) {
                            return new long[]{1, 0, now};
                        }
                        long fails = v[0] + 1;
                        return new long[]{fails, fails >= loginMaxFail ? now + lockMs : 0, now};
                    });
                }
            }
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("账号已停用");
        }
        loginFail.remove(key);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        LoginResult r = new LoginResult();
        r.setToken(tokenService.issue(user.getId(), user.getUsername()));
        r.setUser(user);
        return r;
    }

    private void cleanupLoginFailures(long now) {
        long expiry = loginLockMinutes * 60_000L;
        for (Map.Entry<String, long[]> entry : loginFail.entrySet()) {
            long[] record = entry.getValue();
            long lastFail = record.length > 2 ? record[2] : 0;
            boolean expired = (record[1] > 0 && now >= record[1])
                    || (lastFail > 0 && now - lastFail >= expiry);
            if (expired) {
                loginFail.remove(entry.getKey(), record);
            }
        }
    }

    private void trimLoginFailures(long now) {
        List<Map.Entry<String, long[]>> candidates = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : loginFail.entrySet()) {
            long[] record = entry.getValue();
            if (record[1] <= now) {
                candidates.add(entry);
            }
        }
        candidates.sort((a, b) -> Long.compare(lastFail(a.getValue()), lastFail(b.getValue())));
        int removeCount = Math.max(1, MAX_LOGIN_FAILURE_ENTRIES / 10);
        for (int i = 0; i < removeCount && i < candidates.size(); i++) {
            Map.Entry<String, long[]> entry = candidates.get(i);
            loginFail.remove(entry.getKey(), entry.getValue());
        }
    }

    private static long lastFail(long[] record) {
        return record.length > 2 ? record[2] : 0;
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null || !PasswordHasher.verify(oldPassword, user.getPassword())) {
            throw new BizException("原密码错误");
        }
        User upd = new User();
        upd.setId(userId);
        upd.setPassword(PasswordHasher.hash(requireStrong(newPassword)));
        userMapper.updateById(upd);
    }

    /** 管理员维护：新增必须带密码，修改时密码为空表示不改 */
    public void prepareForSave(User entity, boolean creating) {
        if (entity.getUsername() == null || entity.getUsername().trim().isEmpty()) {
            throw new BizException("用户名不能为空");
        }
        if (!ROLES.contains(entity.getRole())) {
            throw new BizException("角色必须为 " + ROLES);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        entity.setLastLoginAt(null);
        if (entity.getPassword() != null && !entity.getPassword().isEmpty()) {
            entity.setPassword(PasswordHasher.hash(requireStrong(entity.getPassword())));
        } else if (creating) {
            throw new BizException("新建用户必须设置密码");
        }
    }

    public void ensureNotLastAdmin(Long userId, User incoming) {
        User existing = userMapper.selectById(userId);
        if (existing == null || !User.ADMIN.equals(existing.getRole())) {
            return;
        }
        boolean demoted = incoming == null
                || !User.ADMIN.equals(incoming.getRole())
                || (incoming.getStatus() != null && incoming.getStatus() != 1);
        if (!demoted) {
            return;
        }
        Long admins = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, User.ADMIN).eq(User::getStatus, 1).ne(User::getId, userId));
        if (admins == 0) {
            throw new BizException("至少保留一个启用的管理员账号");
        }
    }

    private static String requireStrong(String pwd) {
        if (pwd == null || pwd.length() < 6) {
            throw new BizException("密码长度至少 6 位");
        }
        return pwd;
    }
}
