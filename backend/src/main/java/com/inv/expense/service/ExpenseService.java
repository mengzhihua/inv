package com.inv.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inv.basic.service.BasicService;
import com.inv.common.BizException;
import com.inv.common.CheckCodes;
import com.inv.expense.entity.ExpenseInvoice;
import com.inv.expense.mapper.ExpenseInvoiceMapper;
import com.inv.purchase.entity.InputInvoice;
import com.inv.purchase.mapper.InputInvoiceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** 费用发票：上传、合规检查、报销 */
@Service
@RequiredArgsConstructor
public class ExpenseService {
    private final ExpenseInvoiceMapper mapper;
    private final InputInvoiceMapper inputInvoiceMapper;
    private final BasicService basicService;
    private final ObjectMapper objectMapper;

    @Value("${inv.expense.max-days:180}")
    private int maxDays;
    @Value("${inv.expense.single-limit:50000}")
    private BigDecimal singleLimit;

    @Transactional
    public ExpenseInvoice upload(ExpenseInvoice inv) {
        inv.setId(null);
        inv.setUploadTime(LocalDateTime.now());
        inv.setStatus("UPLOADED");
        inv.setRiskItems(null);
        mapper.insert(inv);
        return inv;
    }

    /** 合规检查：输出风险项，无风险 COMPLIANT，有则 RISK */
    @Transactional
    public ExpenseInvoice complianceCheck(Long id) {
        ExpenseInvoice inv = require(id);
        if ("REIMBURSED".equals(inv.getStatus()) || "REJECTED".equals(inv.getStatus())) {
            throw new BizException("已报销/已驳回的费用票不再检查");
        }
        List<String> risks = new ArrayList<>();
        // DUPLICATE：与进项/其他费用票 code+no 重复
        if (inv.getInvoiceNo() != null) {
            Long inDup = inputInvoiceMapper.selectCount(new LambdaQueryWrapper<InputInvoice>()
                    .eq(InputInvoice::getInvoiceNo, inv.getInvoiceNo())
                    .eq(inv.getInvoiceCode() != null, InputInvoice::getInvoiceCode, inv.getInvoiceCode()));
            if (inDup != null && inDup > 0) {
                risks.add("DUPLICATE");
            } else {
                Long exDup = mapper.selectCount(new LambdaQueryWrapper<ExpenseInvoice>()
                        .eq(ExpenseInvoice::getInvoiceNo, inv.getInvoiceNo())
                        .eq(inv.getInvoiceCode() != null, ExpenseInvoice::getInvoiceCode, inv.getInvoiceCode())
                        .ne(ExpenseInvoice::getId, inv.getId()));
                if (exDup != null && exDup > 0) {
                    risks.add("DUPLICATE");
                }
            }
        }
        // TITLE_MISMATCH：买方税号不属于任何本企业主体
        if (inv.getBuyerTaxNo() != null && !inv.getBuyerTaxNo().isEmpty()
                && basicService.findEntityByTaxNo(inv.getBuyerTaxNo()) == null) {
            risks.add("TITLE_MISMATCH");
        }
        // EXPIRED：开票日期距今超过上限
        if (inv.getIssueDate() != null
                && ChronoUnit.DAYS.between(inv.getIssueDate(), LocalDate.now()) > maxDays) {
            risks.add("EXPIRED");
        }
        // VERIFY_FAILED：校验码后 6 位与派生不一致（提供了校验码时）
        if (inv.getCheckCode() != null && inv.getCheckCode().length() >= 6 && inv.getIssueDate() != null
                && inv.getTotalAmount() != null && inv.getInvoiceNo() != null) {
            String full = CheckCodes.derive(inv.getInvoiceCode(), inv.getInvoiceNo(), inv.getIssueDate(), inv.getTotalAmount());
            if (!inv.getCheckCode().endsWith(full.substring(14))) {
                risks.add("VERIFY_FAILED");
            }
        }
        // AMOUNT_LIMIT：单张超额
        if (inv.getTotalWithTax() != null && inv.getTotalWithTax().compareTo(singleLimit) > 0) {
            risks.add("AMOUNT_LIMIT");
        }
        inv.setRiskItems(risks.isEmpty() ? null : json(risks));
        inv.setStatus(risks.isEmpty() ? "COMPLIANT" : "RISK");
        mapper.updateById(inv);
        return inv;
    }

    /** 批量合规检查；ids 为空时检查全部 UPLOADED */
    @Transactional
    public List<ExpenseInvoice> complianceCheckAll(List<Long> ids) {
        List<Long> targets = ids;
        if (targets == null || targets.isEmpty()) {
            targets = new ArrayList<>();
            for (ExpenseInvoice e : mapper.selectList(new LambdaQueryWrapper<ExpenseInvoice>()
                    .eq(ExpenseInvoice::getStatus, "UPLOADED"))) {
                targets.add(e.getId());
            }
        }
        List<ExpenseInvoice> result = new ArrayList<>();
        for (Long id : targets) {
            result.add(complianceCheck(id));
        }
        return result;
    }

    /** 报销：仅 COMPLIANT */
    @Transactional
    public List<ExpenseInvoice> reimburse(List<Long> ids, String reimburseNo) {
        if (reimburseNo == null || reimburseNo.isEmpty()) {
            throw new BizException("报销单号不能为空");
        }
        List<ExpenseInvoice> out = new ArrayList<>();
        for (Long id : ids) {
            ExpenseInvoice inv = require(id);
            if (!"COMPLIANT".equals(inv.getStatus())) {
                throw new BizException("费用票 " + inv.getInvoiceNo() + " 状态 " + inv.getStatus() + " 不可报销（需 COMPLIANT）");
            }
            inv.setStatus("REIMBURSED");
            inv.setReimburseNo(reimburseNo);
            mapper.updateById(inv);
            out.add(inv);
        }
        return out;
    }

    @Transactional
    public ExpenseInvoice reject(Long id, String reason) {
        ExpenseInvoice inv = require(id);
        if ("REIMBURSED".equals(inv.getStatus())) {
            throw new BizException("已报销费用票不可驳回");
        }
        inv.setStatus("REJECTED");
        inv.setRejectReason(reason);
        mapper.updateById(inv);
        return inv;
    }

    public ExpenseInvoice require(Long id) {
        ExpenseInvoice inv = mapper.selectById(id);
        if (inv == null) {
            throw new BizException("费用发票不存在: " + id);
        }
        return inv;
    }

    private String json(List<String> risks) {
        try {
            return objectMapper.writeValueAsString(risks);
        } catch (JsonProcessingException e) {
            return risks.toString();
        }
    }
}
