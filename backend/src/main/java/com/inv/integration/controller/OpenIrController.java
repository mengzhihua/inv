package com.inv.integration.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inv.common.BizException;
import com.inv.common.R;
import com.inv.integration.client.SrmMatchClient;
import com.inv.purchase.entity.InputInvoice;
import com.inv.purchase.mapper.InputInvoiceMapper;
import com.inv.purchase.service.InputInvoiceService;
import com.inv.sales.entity.Invoice;
import com.inv.sales.entity.InvoiceRequest;
import com.inv.sales.mapper.InvoiceMapper;
import com.inv.sales.mapper.InvoiceRequestMapper;
import com.inv.sales.service.InvoiceRequestService;
import com.inv.sales.service.InvoiceService;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** IR 控制塔：开票申请 / 进项发票快照，以及提交、审核、开具、进项查验。 */
@RestController
@RequestMapping("/api/open/ir")
@RequiredArgsConstructor
public class OpenIrController {
    private final InvoiceRequestMapper requestMapper;
    private final InvoiceRequestService requestService;
    private final InvoiceService invoiceService;
    private final InvoiceMapper invoiceMapper;
    private final InputInvoiceMapper inputMapper;
    private final InputInvoiceService inputService;
    private final SrmMatchClient srmMatchClient;
    private final ConcurrentHashMap<String, Object> actionCache = new ConcurrentHashMap<String, Object>();

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
        for (Invoice invoice : invoiceMapper.selectList(
                new LambdaQueryWrapper<Invoice>().orderByDesc(Invoice::getId))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dataType", "SALES_INVOICE");
            row.put("bizKey", invoice.getInvoiceNo());
            row.put("status", invoice.getStatus());
            row.put("sku", invoice.getInvoiceCode());
            row.put("qty", BigDecimal.ONE);
            row.put("amount", invoice.getTotalWithTax());
            row.put("plantCode", invoice.getInvoiceType());
            row.put("title", invoice.getBuyerName());
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
        return R.ok(executeOnce(cacheKey(type, targetKey, body.get("idempotencyKey")), () -> {
            if ("INV_MATCH_INPUT".equals(type)) {
                return matchInput(targetKey, body);
            }
            if ("INV_VERIFY_INPUT".equals(type)) {
                InputInvoice invoice = inputMapper.selectOne(new LambdaQueryWrapper<InputInvoice>()
                        .eq(InputInvoice::getInvoiceNo, targetKey)
                        .orderByDesc(InputInvoice::getId)
                        .last("LIMIT 1"));
                if (invoice == null) {
                    throw new BizException("进项发票不存在: " + targetKey);
                }
                return inputService.verify(invoice.getId());
            }
            InvoiceRequest request = requestMapper.selectOne(new LambdaQueryWrapper<InvoiceRequest>()
                    .eq(InvoiceRequest::getRequestNo, targetKey));
            if (request == null) {
                throw new BizException("开票申请不存在: " + targetKey);
            }
            if ("INV_SUBMIT_REQUEST".equals(type)) {
                return requestService.transit(request.getId(), "submit", null);
            }
            if ("INV_APPROVE_REQUEST".equals(type)) {
                return requestService.transit(request.getId(), "approve", null);
            }
            if ("INV_ISSUE_REQUEST".equals(type)) {
                return invoiceService.issue(request.getId());
            }
            throw new BizException("不支持的 IR 指令: " + type);
        }));
    }

    @PostMapping("/verify-input")
    public R<Object> verifyInput(@RequestBody Map<String, Object> body) {
        return typedAction("INV_VERIFY_INPUT", body, "invoiceNo");
    }

    @PostMapping("/match-input")
    public R<Object> matchInput(@RequestBody Map<String, Object> body) {
        return typedAction("INV_MATCH_INPUT", body, "invoiceNo");
    }

    @PostMapping("/submit-request")
    public R<Object> submitRequest(@RequestBody Map<String, Object> body) {
        return typedAction("INV_SUBMIT_REQUEST", body, "requestNo");
    }

    @PostMapping("/approve-request")
    public R<Object> approveRequest(@RequestBody Map<String, Object> body) {
        return typedAction("INV_APPROVE_REQUEST", body, "requestNo");
    }

    @PostMapping("/issue-request")
    public R<Object> issueRequest(@RequestBody Map<String, Object> body) {
        return typedAction("INV_ISSUE_REQUEST", body, "requestNo");
    }

    private R<Object> typedAction(String type, Map<String, Object> body, String altKey) {
        if (body == null) {
            body = new LinkedHashMap<String, Object>();
        }
        body.put("type", type);
        if (blank(body.get("targetKey")) && body.get(altKey) != null) {
            body.put("targetKey", body.get(altKey));
        }
        return actions(body);
    }

    private Object matchInput(String invoiceNo, Map<String, Object> body) {
        Map<String, Object> params = nested(body);
        String poNo = firstText(body.get("poNo"), body.get("poCode"), params.get("poNo"), params.get("poCode"));
        BigDecimal poAmount = decimal(firstObj(body.get("poAmount"), params.get("poAmount")));
        BigDecimal receivedQty = decimal(firstObj(body.get("receivedQty"), params.get("receivedQty")));
        BigDecimal invoiceQty = decimal(firstObj(body.get("invoiceQty"), params.get("invoiceQty")));
        String grCode = firstText(body.get("grCode"), body.get("receiptNo"), params.get("grCode"), params.get("receiptNo"));
        Map<String, Object> remote = srmMatchClient.basis(poNo);
        if (remote != null) {
            if (remote.get("poAmount") != null) {
                poAmount = decimal(remote.get("poAmount"));
            }
            if (remote.get("receivedQty") != null) {
                receivedQty = decimal(remote.get("receivedQty"));
            }
            if (remote.get("grCode") != null) {
                grCode = String.valueOf(remote.get("grCode"));
            }
        }
        return inputService.matchThreeWay(invoiceNo, poNo, grCode, poAmount, invoiceQty, receivedQty);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> body) {
        Object params = body == null ? null : body.get("params");
        if (params instanceof Map) {
            return (Map<String, Object>) params;
        }
        return new LinkedHashMap<String, Object>();
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            if (!blank(value)) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private static Object firstObj(Object... values) {
        for (Object value : values) {
            if (value != null && !blank(value)) {
                return value;
            }
        }
        return null;
    }

    private static BigDecimal decimal(Object value) {
        if (value == null || blank(value)) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static boolean blank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty()
                || "null".equals(String.valueOf(value));
    }

    private Object executeOnce(String cacheKey, Supplier<Object> work) {
        if (cacheKey == null) {
            return work.get();
        }
        Object cached = actionCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        synchronized (actionCache) {
            cached = actionCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            Object created = work.get();
            actionCache.put(cacheKey, created);
            return created;
        }
    }

    private static String cacheKey(String type, String targetKey, Object idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String key = String.valueOf(idempotencyKey).trim();
        if (key.isEmpty() || "null".equals(key)) {
            return null;
        }
        return type + "|" + (targetKey == null ? "" : targetKey) + "|" + key;
    }
}
