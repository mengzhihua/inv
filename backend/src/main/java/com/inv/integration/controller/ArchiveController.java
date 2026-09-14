package com.inv.integration.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inv.common.Csv;
import com.inv.common.R;
import com.inv.integration.entity.Archive;
import com.inv.integration.mapper.ArchiveMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 电子档案：按年月归档查询、导出清单 */
@RestController
@RequestMapping("/api/integration/archive")
@RequiredArgsConstructor
public class ArchiveController {
    private final ArchiveMapper mapper;

    @GetMapping("/page")
    public R<Page<Archive>> page(@RequestParam(defaultValue = "1") long current,
                                 @RequestParam(defaultValue = "20") long size,
                                 @RequestParam(required = false) String month,
                                 @RequestParam(required = false) String direction,
                                 @RequestParam(required = false) String invoiceNo) {
        return R.ok(mapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<Archive>()
                .eq(StringUtils.isNotBlank(month), Archive::getIssueMonth, month)
                .eq(StringUtils.isNotBlank(direction), Archive::getDirection, direction)
                .like(StringUtils.isNotBlank(invoiceNo), Archive::getInvoiceNo, invoiceNo)
                .orderByDesc(Archive::getId)));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String month) {
        List<Archive> rows = mapper.selectList(new LambdaQueryWrapper<Archive>()
                .eq(StringUtils.isNotBlank(month), Archive::getIssueMonth, month)
                .orderByAsc(Archive::getId));
        return Csv.download("archive-" + (month == null ? "all" : month) + ".csv",
                new String[]{"invoiceNo", "invoiceType", "direction", "issueMonth", "pdfUrl", "ofdUrl", "meta"},
                rows, a -> new Object[]{a.getInvoiceNo(), a.getInvoiceType(), a.getDirection(),
                        a.getIssueMonth(), a.getPdfUrl(), a.getOfdUrl(), a.getMeta()});
    }
}
