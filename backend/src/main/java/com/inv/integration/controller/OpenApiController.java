package com.inv.integration.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inv.common.BizException;
import com.inv.common.R;
import com.inv.integration.entity.Archive;
import com.inv.integration.mapper.ArchiveMapper;
import com.inv.integration.service.IntegrationLogService;
import com.inv.purchase.entity.InputInvoice;
import com.inv.purchase.service.InputInvoiceService;
import com.inv.sales.entity.Invoice;
import com.inv.sales.entity.InvoiceRequest;
import com.inv.sales.entity.InvoiceRequestLine;
import com.inv.sales.mapper.InvoiceMapper;
import com.inv.sales.service.InvoiceRequestService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对外开放接口（X-Api-Key 校验，由 AuthInterceptor 处理）。
 * OMS/BMS 推送开票申请（source+extRef 幂等）；SRM 推送进项发票。
 */
@RestController
@RequestMapping("/api/open")
@RequiredArgsConstructor
public class OpenApiController {
    private final InvoiceRequestService requestService;
    private final InputInvoiceService inputInvoiceService;
    private final IntegrationLogService logService;
    private final InvoiceMapper invoiceMapper;
    private final ArchiveMapper archiveMapper;

    @Data
    public static class PushRequest {
        private InvoiceRequest request;
        private List<InvoiceRequestLine> lines;
    }

    /** OMS/BMS 推送开票申请；同 source+extRef 重复推送返回原 requestNo */
    @PostMapping("/invoice-requests")
    public R<Map<String, Object>> pushRequest(@RequestBody PushRequest body) {
        long start = System.currentTimeMillis();
        String ref = body.getRequest() == null ? null
                : body.getRequest().getSource() + ":" + body.getRequest().getExtRef();
        try {
            InvoiceRequest req = requestService.create(body.getRequest(), body.getLines());
            Map<String, Object> r = new HashMap<>();
            r.put("requestNo", req.getRequestNo());
            r.put("status", req.getStatus());
            r.put("duplicated", req.getCreatedAt() != null && req.getCreatedAt().isBefore(
                    java.time.LocalDateTime.now().minusSeconds(1)));
            logService.inbound("INVOICE_REQUEST", "PUSH", ref, body, r, null);
            return R.ok(r);
        } catch (RuntimeException e) {
            logService.inbound("INVOICE_REQUEST", "PUSH", ref, body, null, e.getMessage());
            throw e;
        }
    }

    /** 按 source+extRef 查询申请状态与发票号 */
    @GetMapping("/invoice-requests/{source}/{extRef}")
    public R<Map<String, Object>> queryRequest(@PathVariable String source, @PathVariable String extRef) {
        InvoiceRequest req = requestService.findByExtRef(source, extRef);
        if (req == null) {
            throw new BizException("未找到推送的开票申请: " + source + "/" + extRef);
        }
        Map<String, Object> r = new HashMap<>();
        r.put("requestNo", req.getRequestNo());
        r.put("status", req.getStatus());
        r.put("rejectReason", req.getRejectReason());
        if (req.getInvoiceId() != null) {
            Invoice inv = invoiceMapper.selectById(req.getInvoiceId());
            if (inv != null) {
                r.put("invoiceNo", inv.getInvoiceNo());
                r.put("invoiceCode", inv.getInvoiceCode());
            }
        }
        return R.ok(r);
    }

    /** SRM 等外部系统推送进项发票 */
    @PostMapping("/input-invoices")
    public R<Map<String, Object>> pushInput(@RequestBody InputInvoice inv) {
        long start = System.currentTimeMillis();
        try {
            InputInvoice saved = inputInvoiceService.create(inv, null, "API");
            Map<String, Object> r = new HashMap<>();
            r.put("id", saved.getId());
            r.put("invoiceNo", saved.getInvoiceNo());
            logService.inbound("INPUT_INVOICE", "PUSH", inv.getInvoiceNo(), inv, r, null);
            return R.ok(r);
        } catch (RuntimeException e) {
            logService.inbound("INPUT_INVOICE", "PUSH", inv.getInvoiceNo(), inv, null, e.getMessage());
            throw e;
        }
    }

    /** 电子档案查询：按年月归档 */
    @GetMapping("/archive")
    public R<List<Archive>> archive(@RequestParam(required = false) String month,
                                    @RequestParam(required = false) String direction) {
        return R.ok(archiveMapper.selectList(new LambdaQueryWrapper<Archive>()
                .eq(month != null, Archive::getIssueMonth, month)
                .eq(direction != null, Archive::getDirection, direction)
                .orderByDesc(Archive::getId)));
    }
}
