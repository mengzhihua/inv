package com.inv.purchase.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inv.common.Csv;
import com.inv.common.R;
import com.inv.purchase.entity.DeductionBatch;
import com.inv.purchase.entity.InputInvoice;
import com.inv.purchase.entity.InputInvoiceLine;
import com.inv.purchase.mapper.DeductionBatchMapper;
import com.inv.purchase.mapper.InputInvoiceMapper;
import com.inv.purchase.service.InputInvoiceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase")
@RequiredArgsConstructor
public class InputInvoiceController {
    private final InputInvoiceService service;
    private final InputInvoiceMapper mapper;
    private final DeductionBatchMapper batchMapper;

    @Data
    public static class InvoiceForm {
        private InputInvoice invoice;
        private List<InputInvoiceLine> lines;
        private String source;
    }

    @Data
    public static class CheckReq {
        private String period;
    }

    @Data
    public static class MatchReq {
        private String poNo;
        private String receiptNo;
        private BigDecimal amount;
    }

    @Data
    public static class ReasonReq {
        private String reason;
    }

    @Data
    public static class ConfirmReq {
        private Long taxEntityId;
        private String period;
    }

    @GetMapping("/input/page")
    public R<Page<InputInvoice>> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "20") long size,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String verifyStatus,
                                      @RequestParam(required = false) String deductStatus,
                                      @RequestParam(required = false) String deductPeriod,
                                      @RequestParam(required = false) String matchStatus,
                                      @RequestParam(required = false) String invoiceType,
                                      @RequestParam(required = false) String sellerName,
                                      @RequestParam(required = false) String invoiceNo) {
        return R.ok(mapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<InputInvoice>()
                .eq(StringUtils.isNotBlank(status), InputInvoice::getStatus, status)
                .eq(StringUtils.isNotBlank(verifyStatus), InputInvoice::getVerifyStatus, verifyStatus)
                .eq(StringUtils.isNotBlank(deductStatus), InputInvoice::getDeductStatus, deductStatus)
                .eq(StringUtils.isNotBlank(deductPeriod), InputInvoice::getDeductPeriod, deductPeriod)
                .eq(StringUtils.isNotBlank(matchStatus), InputInvoice::getMatchStatus, matchStatus)
                .eq(StringUtils.isNotBlank(invoiceType), InputInvoice::getInvoiceType, invoiceType)
                .like(StringUtils.isNotBlank(sellerName), InputInvoice::getSellerName, sellerName)
                .like(StringUtils.isNotBlank(invoiceNo), InputInvoice::getInvoiceNo, invoiceNo)
                .orderByDesc(InputInvoice::getId)));
    }

    @GetMapping("/input/{id}")
    public R<Map<String, Object>> get(@PathVariable Long id) {
        Map<String, Object> r = new HashMap<>();
        r.put("invoice", service.require(id));
        r.put("lines", service.linesOf(id));
        return R.ok(r);
    }

    @PostMapping("/input")
    public R<InputInvoice> create(@RequestBody InvoiceForm form) {
        return R.ok(service.create(form.getInvoice(), form.getLines(), form.getSource()));
    }

    /** OCR 模拟：接收文本字段直接落库为 SCAN 来源 */
    @PostMapping("/input/scan")
    public R<InputInvoice> scan(@RequestBody InputInvoice inv) {
        return R.ok(service.create(inv, null, "SCAN"));
    }

    /** CSV 导入（列：invoiceType,invoiceCode,invoiceNo,issueDate,sellerName,sellerTaxNo,buyerName,buyerTaxNo,totalAmount,totalTax,totalWithTax,checkCode） */
    @PostMapping("/input/import")
    public R<Map<String, Object>> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        List<String[]> rows = Csv.read(file.getInputStream());
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            String[] c = rows.get(i);
            try {
                InputInvoice inv = new InputInvoice();
                inv.setInvoiceType(c[0]);
                inv.setInvoiceCode(c.length > 1 && !c[1].isEmpty() ? c[1] : null);
                inv.setInvoiceNo(c[2]);
                inv.setIssueDate(LocalDate.parse(c[3]));
                inv.setSellerName(c[4]);
                inv.setSellerTaxNo(c.length > 5 ? c[5] : null);
                inv.setBuyerName(c.length > 6 ? c[6] : null);
                inv.setBuyerTaxNo(c.length > 7 ? c[7] : null);
                inv.setTotalAmount(new BigDecimal(c[8]));
                inv.setTotalTax(new BigDecimal(c[9]));
                inv.setTotalWithTax(new BigDecimal(c[10]));
                inv.setCheckCode(c.length > 11 ? c[11] : null);
                service.create(inv, null, "IMPORT");
                ok++;
            } catch (RuntimeException e) {
                errors.add("第" + (i + 1) + "行: " + e.getMessage());
            }
        }
        Map<String, Object> r = new HashMap<>();
        r.put("imported", ok);
        r.put("errors", errors);
        return R.ok(r);
    }

    @PostMapping("/input/{id}/verify")
    public R<InputInvoice> verify(@PathVariable Long id) {
        return R.ok(service.verify(id));
    }

    @PostMapping("/input/batch-verify")
    public R<List<InputInvoice>> batchVerify(@RequestBody Map<String, List<Long>> body) {
        List<InputInvoice> out = new ArrayList<>();
        for (Long id : body.get("ids")) {
            out.add(service.verify(id));
        }
        return R.ok(out);
    }

    @PostMapping("/input/{id}/check")
    public R<InputInvoice> check(@PathVariable Long id, @RequestBody CheckReq req) {
        return R.ok(service.check(id, req.getPeriod()));
    }

    @PostMapping("/input/{id}/uncheck")
    public R<InputInvoice> uncheck(@PathVariable Long id) {
        return R.ok(service.uncheck(id));
    }

    @PostMapping("/input/{id}/not-deduct")
    public R<InputInvoice> notDeduct(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        return R.ok(service.notDeduct(id, req == null ? null : req.getReason()));
    }

    @PostMapping("/deduction/confirm")
    public R<DeductionBatch> confirmDeduction(@RequestBody ConfirmReq req) {
        return R.ok(service.confirmDeduction(req.getTaxEntityId(), req.getPeriod()));
    }

    @GetMapping("/deduction/page")
    public R<Page<DeductionBatch>> batches(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "20") long size,
                                           @RequestParam(required = false) String period) {
        return R.ok(batchMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<DeductionBatch>()
                .eq(StringUtils.isNotBlank(period), DeductionBatch::getPeriod, period)
                .orderByDesc(DeductionBatch::getId)));
    }

    @PostMapping("/input/{id}/match")
    public R<InputInvoice> match(@PathVariable Long id, @RequestBody MatchReq req) {
        return R.ok(service.match(id, req.getPoNo(), req.getReceiptNo(), req.getAmount()));
    }

    @PostMapping("/input/{id}/post")
    public R<InputInvoice> post(@PathVariable Long id) {
        return R.ok(service.post(id));
    }

    @GetMapping("/input/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String deductStatus) {
        List<InputInvoice> rows = mapper.selectList(new LambdaQueryWrapper<InputInvoice>()
                .eq(StringUtils.isNotBlank(deductStatus), InputInvoice::getDeductStatus, deductStatus)
                .orderByDesc(InputInvoice::getId));
        return Csv.download("input-invoices.csv",
                new String[]{"invoiceType", "invoiceCode", "invoiceNo", "issueDate", "sellerName",
                        "totalAmount", "totalTax", "verifyStatus", "deductStatus", "deductPeriod"},
                rows, i -> new Object[]{i.getInvoiceType(), i.getInvoiceCode(), i.getInvoiceNo(),
                        i.getIssueDate(), i.getSellerName(), i.getTotalAmount(), i.getTotalTax(),
                        i.getVerifyStatus(), i.getDeductStatus(), i.getDeductPeriod()});
    }

    /** CSV 导入模板 */
    @GetMapping("/input/import-template")
    public ResponseEntity<byte[]> template() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"SPECIAL", "032002300001", "10001001", "2025-01-15", "供应商名称",
                "91XXXXXXXXXXXXXXXX", "云途科技股份有限公司", "91310000780000001A", "1000.00", "130.00", "1130.00", "88888888888888888888"});
        return Csv.download("input-import-template.csv",
                new String[]{"invoiceType", "invoiceCode", "invoiceNo", "issueDate", "sellerName",
                        "sellerTaxNo", "buyerName", "buyerTaxNo", "totalAmount", "totalTax", "totalWithTax", "checkCode"},
                rows, r -> r);
    }
}
