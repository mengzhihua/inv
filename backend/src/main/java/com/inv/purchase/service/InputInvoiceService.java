package com.inv.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inv.basic.entity.Partner;
import com.inv.basic.entity.TaxEntity;
import com.inv.basic.service.BasicService;
import com.inv.common.BizException;
import com.inv.common.CheckCodes;
import com.inv.common.CodeGenerator;
import com.inv.common.TaxCalc;
import com.inv.purchase.entity.DeductionBatch;
import com.inv.purchase.entity.InputInvoice;
import com.inv.purchase.entity.InputInvoiceLine;
import com.inv.purchase.mapper.DeductionBatchMapper;
import com.inv.purchase.mapper.InputInvoiceLineMapper;
import com.inv.purchase.mapper.InputInvoiceMapper;
import com.inv.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 进项发票：录入查重/查验/勾选抵扣/三单匹配/入账 */
@Service
@RequiredArgsConstructor
public class InputInvoiceService {
    /** 可勾选抵扣的票种（专票类） */
    private static final Set<String> DEDUCTIBLE = new HashSet<>(Arrays.asList("SPECIAL", "E_SPECIAL", "ALL_ELECTRIC"));
    /** 三单匹配容差 */
    private static final BigDecimal MATCH_TOLERANCE = new BigDecimal("0.06");

    private final InputInvoiceMapper mapper;
    private final InputInvoiceLineMapper lineMapper;
    private final DeductionBatchMapper batchMapper;
    private final BasicService basicService;
    private final TaxService taxService;
    private final CodeGenerator codeGenerator;

    // ==================== 录入 ====================
    @Transactional
    public InputInvoice create(InputInvoice inv, List<InputInvoiceLine> lines, String source) {
        if (inv.getInvoiceNo() == null || inv.getInvoiceNo().isEmpty()) {
            throw new BizException("发票号码不能为空");
        }
        // 查重：(invoiceCode, invoiceNo) 唯一；数电票只看号码
        InputInvoice dup = findDup(inv.getInvoiceCode(), inv.getInvoiceNo());
        if (dup != null) {
            throw new BizException("DUPLICATE：发票重复（" + inv.getInvoiceNo() + "，已录入单号 ID=" + dup.getId() + "）");
        }
        inv.setId(null);
        inv.setSource(source == null ? "MANUAL" : source);
        if (inv.getVerifyStatus() == null) {
            inv.setVerifyStatus("UNVERIFIED");
        }
        if (inv.getStatus() == null) {
            inv.setStatus("NORMAL");
        }
        if (inv.getDeductStatus() == null) {
            inv.setDeductStatus("PENDING");
        }
        if (inv.getMatchStatus() == null) {
            inv.setMatchStatus("UNMATCHED");
        }
        if (inv.getAccountStatus() == null) {
            inv.setAccountStatus("UNPOSTED");
        }
        // 归属主体：按买方税号匹配本企业主体
        TaxEntity entity = basicService.findEntityByTaxNo(inv.getBuyerTaxNo());
        inv.setTaxEntityId(entity == null ? inv.getTaxEntityId() : entity.getId());
        // 供应商：按卖方税号匹配往来单位
        Partner supplier = basicService.findPartnerByTaxNo(inv.getSellerTaxNo());
        inv.setSupplierId(supplier == null ? inv.getSupplierId() : supplier.getId());
        try {
            mapper.insert(inv);
        } catch (DuplicateKeyException e) {
            throw new BizException("DUPLICATE：发票重复（" + inv.getInvoiceNo() + "）");
        }
        if (lines != null) {
            for (InputInvoiceLine l : lines) {
                l.setId(null);
                l.setInputInvoiceId(inv.getId());
                lineMapper.insert(l);
            }
        }
        return inv;
    }

    private InputInvoice findDup(String code, String no) {
        List<InputInvoice> list = mapper.selectList(new LambdaQueryWrapper<InputInvoice>()
                .eq(InputInvoice::getInvoiceNo, no)
                .and(w -> w.eq(InputInvoice::getInvoiceCode, code).or().isNull(InputInvoice::getInvoiceCode)));
        for (InputInvoice i : list) {
            if (code == null || code.isEmpty() || i.getInvoiceCode() == null || i.getInvoiceCode().isEmpty()
                    || code.equals(i.getInvoiceCode())) {
                return i;
            }
        }
        return null;
    }

    // ==================== 查验 ====================
    /** 模拟全国查验平台：校验码后 6 位由 code+no+date+amount 派生 */
    public static String expectedCheckCodeTail(InputInvoice inv) {
        String full = CheckCodes.derive(inv.getInvoiceCode(), inv.getInvoiceNo(), inv.getIssueDate(), inv.getTotalAmount());
        return full.substring(14);
    }

    @Transactional
    public InputInvoice verify(Long id) {
        InputInvoice inv = require(id);
        String expected = expectedCheckCodeTail(inv);
        boolean codeOk = inv.getCheckCode() != null && inv.getCheckCode().length() >= 6
                && inv.getCheckCode().endsWith(expected);
        boolean titleOk = basicService.findEntityByTaxNo(inv.getBuyerTaxNo()) != null;
        inv.setVerifiedAt(LocalDateTime.now());
        if (!codeOk) {
            inv.setVerifyStatus("FAILED");
            inv.setVerifyMsg("校验码不一致，疑似假票");
        } else if (!titleOk) {
            inv.setVerifyStatus("VERIFIED");
            inv.setStatus("ABNORMAL");
            inv.setVerifyMsg("查验通过但抬头不符：买方税号不属于本企业主体");
        } else {
            inv.setVerifyStatus("VERIFIED");
            inv.setVerifyMsg("查验一致");
        }
        mapper.updateById(inv);
        return inv;
    }

    // ==================== 勾选抵扣 ====================
    @Transactional
    public InputInvoice check(Long id, String period) {
        InputInvoice inv = require(id);
        if (!DEDUCTIBLE.contains(inv.getInvoiceType())) {
            throw new BizException("仅专票/电子专票/数电票（专票）可勾选抵扣");
        }
        if (!"VERIFIED".equals(inv.getVerifyStatus())) {
            throw new BizException("发票未查验通过，不可勾选");
        }
        if (!"NORMAL".equals(inv.getStatus())) {
            throw new BizException("发票状态异常（" + inv.getStatus() + "），不可勾选");
        }
        if (!"PENDING".equals(inv.getDeductStatus())) {
            throw new BizException("发票抵扣状态为 " + inv.getDeductStatus() + "，不可重复勾选");
        }
        if (period == null || !period.matches("^\\d{4}-\\d{2}$")) {
            throw new BizException("属期格式须为 yyyy-MM");
        }
        taxService.requireOpen(inv.getTaxEntityId(), period);
        inv.setDeductStatus("CHECKED");
        inv.setDeductPeriod(period);
        mapper.updateById(inv);
        return inv;
    }

    @Transactional
    public InputInvoice uncheck(Long id) {
        InputInvoice inv = require(id);
        if (!"CHECKED".equals(inv.getDeductStatus())) {
            throw new BizException("仅已勾选发票可取消勾选");
        }
        taxService.requireOpen(inv.getTaxEntityId(), inv.getDeductPeriod());
        inv.setDeductStatus("PENDING");
        inv.setDeductPeriod(null);
        mapper.updateById(inv);
        return inv;
    }

    /** 确认抵扣：该期 CHECKED → DEDUCTED，并生成 DeductionBatch 统计 */
    @Transactional
    public DeductionBatch confirmDeduction(Long taxEntityId, String period) {
        List<InputInvoice> checked = mapper.selectList(new LambdaQueryWrapper<InputInvoice>()
                .eq(taxEntityId != null, InputInvoice::getTaxEntityId, taxEntityId)
                .eq(InputInvoice::getDeductStatus, "CHECKED")
                .eq(InputInvoice::getDeductPeriod, period));
        if (checked.isEmpty()) {
            throw new BizException("属期 " + period + " 无已勾选发票");
        }
        BigDecimal amount = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        for (InputInvoice inv : checked) {
            taxService.requireOpen(inv.getTaxEntityId(), period);
            inv.setDeductStatus("DEDUCTED");
            mapper.updateById(inv);
            amount = amount.add(inv.getTotalAmount() == null ? BigDecimal.ZERO : inv.getTotalAmount());
            tax = tax.add(inv.getTotalTax() == null ? BigDecimal.ZERO : inv.getTotalTax());
        }
        DeductionBatch b = new DeductionBatch();
        b.setTaxEntityId(taxEntityId);
        b.setPeriod(period);
        b.setBatchNo(codeGenerator.next("DED"));
        b.setInvoiceCount(checked.size());
        b.setTotalAmount(TaxCalc.round(amount));
        b.setTotalTax(TaxCalc.round(tax));
        batchMapper.insert(b);
        return b;
    }

    /** 标记不抵扣 */
    @Transactional
    public InputInvoice notDeduct(Long id, String reason) {
        InputInvoice inv = require(id);
        if (!"PENDING".equals(inv.getDeductStatus()) && !"CHECKED".equals(inv.getDeductStatus())) {
            throw new BizException("当前抵扣状态不允许标记不抵扣");
        }
        inv.setDeductStatus("NOT_DEDUCT");
        inv.setNotDeductReason(reason);
        inv.setDeductPeriod(null);
        mapper.updateById(inv);
        return inv;
    }

    // ==================== 三单匹配 ====================
    @Transactional
    public InputInvoice match(Long id, String poNo, String receiptNo, BigDecimal amount) {
        InputInvoice inv = require(id);
        inv.setPoNo(poNo);
        inv.setReceiptNo(receiptNo);
        if (amount == null || inv.getTotalWithTax() == null) {
            throw new BizException("匹配金额缺失");
        }
        BigDecimal diff = inv.getTotalWithTax().subtract(amount).abs();
        inv.setMatchDiff(diff);
        inv.setMatchStatus(diff.compareTo(MATCH_TOLERANCE) <= 0 ? "MATCHED" : "MISMATCH");
        mapper.updateById(inv);
        return inv;
    }

    /** 用采购金额和收货数量做三单匹配。数量缺省时汇总发票行。 */
    @Transactional
    public InputInvoice matchThreeWay(String invoiceNo, String poNo, String receiptNo,
                                      BigDecimal poAmount, BigDecimal invoiceQty, BigDecimal receivedQty) {
        InputInvoice inv = mapper.selectOne(new LambdaQueryWrapper<InputInvoice>()
                .eq(InputInvoice::getInvoiceNo, invoiceNo)
                .orderByDesc(InputInvoice::getId)
                .last("LIMIT 1"));
        if (inv == null) {
            throw new BizException("进项发票不存在: " + invoiceNo);
        }
        BigDecimal qty = invoiceQty;
        if (qty == null) {
            BigDecimal summed = BigDecimal.ZERO;
            boolean any = false;
            for (InputInvoiceLine line : linesOf(inv.getId())) {
                if (line.getQuantity() != null) {
                    summed = summed.add(line.getQuantity());
                    any = true;
                }
            }
            qty = any ? summed : null;
        }
        inv.setPoNo(poNo);
        inv.setReceiptNo(receiptNo);
        if (inv.getTotalWithTax() != null && poAmount != null) {
            inv.setMatchDiff(inv.getTotalWithTax().subtract(poAmount).abs());
        }
        inv.setMatchStatus(ThreeWayMatch.judge(inv.getTotalWithTax(), poAmount, qty, receivedQty));
        mapper.updateById(inv);
        return inv;
    }

    // ==================== 入账 ====================
    @Transactional
    public InputInvoice post(Long id) {
        InputInvoice inv = require(id);
        if ("POSTED".equals(inv.getAccountStatus())) {
            throw new BizException("发票已入账");
        }
        inv.setAccountStatus("POSTED");
        inv.setVoucherNo(codeGenerator.next("VCH"));
        mapper.updateById(inv);
        return inv;
    }

    public InputInvoice require(Long id) {
        InputInvoice inv = mapper.selectById(id);
        if (inv == null) {
            throw new BizException("进项发票不存在: " + id);
        }
        return inv;
    }

    public List<InputInvoiceLine> linesOf(Long id) {
        return lineMapper.selectList(new LambdaQueryWrapper<InputInvoiceLine>()
                .eq(InputInvoiceLine::getInputInvoiceId, id).orderByAsc(InputInvoiceLine::getId));
    }
}
