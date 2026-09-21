package com.inv.integration.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inv.common.BizException;
import com.inv.common.R;
import com.inv.purchase.entity.InputInvoice;
import com.inv.purchase.mapper.InputInvoiceMapper;
import com.inv.purchase.service.InputInvoiceService;
import com.inv.sales.entity.InvoiceRequest;
import com.inv.sales.mapper.InvoiceRequestMapper;
import com.inv.sales.service.InvoiceRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** IR 控制塔：开票申请 / 进项发票快照，以及提交、审核、进项查验。 */
@RestController
@RequestMapping("/api/open/ir")
@RequiredArgsConstructor
public class OpenIrController {
    private final InvoiceRequestMapper requestMapper;
    private final InvoiceRequestService requestService;
    private final InputInvoiceMapper inputMapper;
    private final InputInvoiceService inputService;

    @GetMapping("/snapshots")
    public R<Map<String, Object>> snapshots() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (InvoiceRequest request : requestMapper.selectList(
                new LambdaQueryWrapper<InvoiceRequest>().orderByDesc(InvoiceRequest::getId))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dataType", "INVOICE_REQUEST");
            row.put("bizKey", request.getRequestNo());
            row.put("status", request.getStatus());
            row.put("sku", request.getExtRef());
            row.put("qty", BigDecimal.ONE);
            row.put("amount", request.getTotalWithTax());
            row.put("plantCode", request.getSource());
            row.put("title", request.getBuyerName());
            rows.add(row);
        }
        for (InputInvoice invoice : inputMapper.selectList(
                new LambdaQueryWrapper<InputInvoice>().orderByDesc(InputInvoice::getId))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dataType", "INPUT_INVOICE");
            row.put("bizKey", invoice.getInvoiceNo());
            row.put("status", invoice.getVerifyStatus());
            row.put("sku", invoice.getInvoiceCode());
            row.put("qty", BigDecimal.ONE);
            row.put("amount", invoice.getTotalWithTax());
            row.put("plantCode", invoice.getPoNo());
            row.put("title", invoice.getSellerName());
            rows.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("system", "INV");
        data.put("snapshots", rows);
        return R.ok(data);
    }

    @PostMapping("/actions")
    public R<Object> actions(@RequestBody Map<String, Object> body) {
        String type = String.valueOf(body.getOrDefault("type", ""));
        String targetKey = String.valueOf(body.getOrDefault("targetKey", ""));
        if ("INV_VERIFY_INPUT".equals(type)) {
            InputInvoice invoice = inputMapper.selectOne(new LambdaQueryWrapper<InputInvoice>()
                    .eq(InputInvoice::getInvoiceNo, targetKey)
                    .orderByDesc(InputInvoice::getId)
                    .last("LIMIT 1"));
            if (invoice == null) {
                throw new BizException("进项发票不存在: " + targetKey);
            }
            return R.ok(inputService.verify(invoice.getId()));
        }
        InvoiceRequest request = requestMapper.selectOne(new LambdaQueryWrapper<InvoiceRequest>()
                .eq(InvoiceRequest::getRequestNo, targetKey));
        if (request == null) {
            throw new BizException("开票申请不存在: " + targetKey);
        }
        if ("INV_SUBMIT_REQUEST".equals(type)) {
            return R.ok(requestService.transit(request.getId(), "submit", null));
        }
        if ("INV_APPROVE_REQUEST".equals(type)) {
            return R.ok(requestService.transit(request.getId(), "approve", null));
        }
        throw new BizException("不支持的 IR 指令: " + type);
    }
}
