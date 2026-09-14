package com.inv.system.controller;

import com.inv.common.BaseCrudController;
import com.inv.common.BizException;
import com.inv.common.R;
import com.inv.system.auth.CurrentUser;
import com.inv.system.entity.User;
import com.inv.system.mapper.UserMapper;
import com.inv.system.service.UserService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/user")
public class UserController extends BaseCrudController<User, UserMapper> {
    private final UserService userService;

    public UserController(UserService userService) {
        super(User.class);
        this.userService = userService;
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"username", "real_name"};
    }

    @Override
    public R<User> create(@RequestBody User entity) {
        userService.prepareForSave(entity, true);
        return super.create(entity);
    }

    @Override
    public R<User> update(@PathVariable Long id, @RequestBody User entity) {
        userService.prepareForSave(entity, false);
        userService.ensureNotLastAdmin(id, entity);
        return super.update(id, entity);
    }

    @Override
    public R<Void> delete(@PathVariable Long id) {
        if (id.equals(CurrentUser.get().getId())) {
            throw new BizException("不能删除当前登录账号");
        }
        userService.ensureNotLastAdmin(id, null);
        return super.delete(id);
    }
}
