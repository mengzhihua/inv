package com.inv.system;

import com.inv.common.BizException;
import com.inv.system.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class AuthSecurityTest {

    @Autowired
    UserService userService;

    @Test
    void locksUsernameAfterFiveFailedLogins() {
        String username = "missing-login-lock-test";
        for (int i = 0; i < 5; i++) {
            BizException ex = assertThrows(BizException.class,
                    () -> userService.login(username, "wrong-password"));
            assertEquals("用户名或密码错误", ex.getMessage());
        }
        BizException locked = assertThrows(BizException.class,
                () -> userService.login(username, "wrong-password"));
        assertEquals("账号已锁定，请稍后再试", locked.getMessage());
    }
}
