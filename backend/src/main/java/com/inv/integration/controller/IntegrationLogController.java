package com.inv.integration.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inv.common.R;
import com.inv.integration.entity.IntegrationLog;
import com.inv.integration.mapper.IntegrationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration/log")
@RequiredArgsConstructor
public class IntegrationLogController {
    private final IntegrationLogMapper mapper;

    @GetMapping("/page")
    public R<Page<IntegrationLog>> page(@RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "20") long size,
                                        @RequestParam(required = false) String target,
                                        @RequestParam(required = false) String direction,
                                        @RequestParam(required = false) String action,
                                        @RequestParam(required = false) Integer success,
                                        @RequestParam(required = false) String refNo) {
        LambdaQueryWrapper<IntegrationLog> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.isNotBlank(target), IntegrationLog::getTarget, target)
                .eq(StringUtils.isNotBlank(direction), IntegrationLog::getDirection, direction)
                .eq(StringUtils.isNotBlank(action), IntegrationLog::getAction, action)
                .eq(success != null, IntegrationLog::getSuccess, success)
                .like(StringUtils.isNotBlank(refNo), IntegrationLog::getRefNo, refNo)
                .orderByDesc(IntegrationLog::getId);
        return R.ok(mapper.selectPage(new Page<>(current, size), qw));
    }
}
