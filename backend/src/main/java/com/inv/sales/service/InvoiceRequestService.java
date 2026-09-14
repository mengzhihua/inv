package com.inv.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inv.basic.entity.Partner;
import com.inv.basic.entity.TaxEntity;
import com.inv.basic.mapper.PartnerMapper;
import com.inv.basic.service.BasicService;
import com.inv.common.BizException;
import com.inv.common.CodeGenerator;
import com.inv.common.TaxCalc;
import com.inv.sales.entity.InvoiceRequest;
import com.inv.sales.entity.InvoiceRequestLine;
import com.inv.sales.mapper.InvoiceRequestLineMapper;
import com.inv.sales.mapper.InvoiceRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 开票申请：录入/校验/提交/审核/拆分/合并 */
@Service
@RequiredArgsConstructor
public class InvoiceRequestService {
    public static final Set<String> INVOICE_TYPES = new HashSet<>(
            Arrays.asList("SPECIAL", "NORMAL", "E_NORMAL", "E_SPECIAL", "ALL_ELECTRIC"));

    private final InvoiceRequestMapper requestMapper;
    private final InvoiceRequestLineMapper lineMapper;
    private final PartnerMapper partnerMapper;
    private final BasicService basicService;
    private final CodeGenerator codeGenerator;

    public List<InvoiceRequestLine> linesOf(Long requestId) {
        return lineMapper.selectList(new LambdaQueryWrapper<InvoiceRequestLine>()
                .eq(InvoiceRequestLine::getRequestId, requestId).orderByAsc(InvoiceRequestLine::getId));
    }

    /** source+extRef 幂等查询 */
    public InvoiceRequest findByExtRef(String source, String extRef) {
        if (source == null || extRef == null) {
            return null;
        }
        return requestMapper.selectOne(new LambdaQueryWrapper<InvoiceRequest>()
                .eq(InvoiceRequest::getSource, source)
                .eq(InvoiceRequest::getExtRef, extRef));
    }

    /**
     * 校验并计算行与合计（含税录入自动反算）。
     * 行规则：priceIncludeTax=1 时 amount 视为含税金额，反算不含税与税额；
     * 否则 tax=taxOf(amount,rate)，声明税额与合计需在 ±0.06 容差内。
     */
    public void validateAndCalc(InvoiceRequest req, List<InvoiceRequestLine> lines) {
        if (req.getInvoiceType() == null || !INVOICE_TYPES.contains(req.getInvoiceType())) {
            throw new BizException("发票类型不正确，须为 " + INVOICE_TYPES);
        }
        TaxEntity entity = basicService.requireTaxEntity(req.getTaxEntityId());
        // 买方信息：申请时从往来单位快照，可覆盖
        if (req.getCustomerId() != null) {
            Partner p = partnerMapper.selectById(req.getCustomerId());
            if (p != null) {
                if (req.getBuyerName() == null) {
                    req.setBuyerName(p.getName());
                }
                if (req.getBuyerTaxNo() == null) {
                    req.setBuyerTaxNo(p.getTaxNo());
                }
                if (req.getBuyerAddressPhone() == null) {
                    String ap = (p.getAddress() == null ? "" : p.getAddress())
                            + (p.getPhone() == null ? "" : " " + p.getPhone());
                    req.setBuyerAddressPhone(ap.trim());
                }
                if (req.getBuyerBank() == null && p.getBankName() != null) {
                    req.setBuyerBank(p.getBankName() + " " + (p.getBankAccount() == null ? "" : p.getBankAccount()));
                }
            }
        }
        if (req.getBuyerName() == null || req.getBuyerName().isEmpty()) {
            throw new BizException("买方名称不能为空");
        }
        boolean special = "SPECIAL".equals(req.getInvoiceType()) || "E_SPECIAL".equals(req.getInvoiceType());
        if (special) {
            if (req.getBuyerTaxNo() == null || req.getBuyerTaxNo().isEmpty()) {
                throw new BizException("专票/电子专票买方税号必填");
            }
            if (req.getBuyerAddressPhone() == null || req.getBuyerAddressPhone().isEmpty()) {
                throw new BizException("专票/电子专票买方地址电话必填");
            }
            if (req.getBuyerBank() == null || req.getBuyerBank().isEmpty()) {
                throw new BizException("专票/电子专票买方开户行及账号必填");
            }
        } else if ((req.getBuyerTaxNo() == null || req.getBuyerTaxNo().isEmpty())
                && !isIndividual(req)) {
            throw new BizException("普票买方为企业时税号必填（个人可空）");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BizException("开票明细不能为空");
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        for (InvoiceRequestLine l : lines) {
            BigDecimal rate = l.getTaxRate() == null ? new BigDecimal("0.13") : l.getTaxRate();
            l.setTaxRate(rate);
            if (l.getPriceIncludeTax() != null && l.getPriceIncludeTax() == 1) {
                // 含税录入：amount 字段传入含税金额
                if (l.getAmount() == null) {
                    throw new BizException("含税录入行缺少含税金额");
                }
                BigDecimal[] back = TaxCalc.fromIncludeTax(l.getAmount(), rate);
                l.setAmount(back[0]);
                l.setTaxAmount(back[1]);
                if (l.getUnitPrice() == null && l.getQuantity() != null && l.getQuantity().signum() != 0) {
                    l.setUnitPrice(back[0].divide(l.getQuantity(), 6, java.math.RoundingMode.HALF_UP));
                }
                totalAmount = totalAmount.add(back[0]);
                totalTax = totalTax.add(back[1]);
            } else {
                if (l.getAmount() == null) {
                    if (l.getQuantity() == null || l.getUnitPrice() == null) {
                        throw new BizException("明细行缺少金额或数量/单价");
                    }
                    l.setAmount(TaxCalc.round(l.getQuantity().multiply(l.getUnitPrice())));
                }
                BigDecimal tax = TaxCalc.taxOf(l.getAmount(), rate);
                if (l.getTaxAmount() != null && !TaxCalc.withinTolerance(l.getTaxAmount(), tax)) {
                    throw new BizException("明细行[" + l.getGoodsName() + "]税额与税率不符（容差 ±0.06）");
                }
                l.setTaxAmount(tax);
                totalAmount = totalAmount.add(l.getAmount());
                totalTax = totalTax.add(tax);
            }
        }
        BigDecimal totalWithTax = totalAmount.add(totalTax);
        if (!TaxCalc.withinTolerance(req.getTotalAmount(), totalAmount)
                || !TaxCalc.withinTolerance(req.getTotalTax(), totalTax)) {
            throw new BizException("申请合计与明细合计不一致（容差 ±0.06）");
        }
        req.setTotalAmount(TaxCalc.round(totalAmount));
        req.setTotalTax(TaxCalc.round(totalTax));
        req.setTotalWithTax(TaxCalc.round(totalWithTax));
        // 限额
        BigDecimal limit = basicService.invoiceLimit(entity.getId(), req.getInvoiceType());
        if (limit != null && req.getTotalWithTax().compareTo(limit) > 0) {
            throw new BizException("单张含税金额超过该票种限额 " + limit + "，请使用拆分接口");
        }
        // 明细 > 8 行的专票标记销货清单
        req.setWithList(special && lines.size() > 8 ? 1 : 0);
    }

    private boolean isIndividual(InvoiceRequest req) {
        if (req.getBuyerName() != null && req.getBuyerName().contains("个人")) {
            return true;
        }
        if (req.getCustomerId() != null) {
            Partner p = partnerMapper.selectById(req.getCustomerId());
            return p != null && (p.getTaxNo() == null || p.getTaxNo().isEmpty());
        }
        return false;
    }

    @Transactional
    public InvoiceRequest create(InvoiceRequest req, List<InvoiceRequestLine> lines) {
        if (req.getSource() == null) {
            req.setSource("MANUAL");
        }
        // source+extRef 幂等
        InvoiceRequest existed = findByExtRef(req.getSource(), req.getExtRef());
        if (existed != null) {
            return existed;
        }
        validateAndCalc(req, lines);
        req.setId(null);
        req.setRequestNo(codeGenerator.next("REQ"));
        req.setStatus("DRAFT");
        requestMapper.insert(req);
        for (InvoiceRequestLine l : lines) {
            l.setId(null);
            l.setRequestId(req.getId());
            lineMapper.insert(l);
        }
        return req;
    }

    @Transactional
    public InvoiceRequest update(Long id, InvoiceRequest req, List<InvoiceRequestLine> lines) {
        InvoiceRequest old = require(id);
        if (!"DRAFT".equals(old.getStatus()) && !"REJECTED".equals(old.getStatus())) {
            throw new BizException("仅草稿/已退回状态可编辑");
        }
        req.setId(id);
        req.setRequestNo(old.getRequestNo());
        req.setStatus("DRAFT");
        req.setRejectReason(null);
        req.setSource(old.getSource());
        req.setExtRef(old.getExtRef());
        validateAndCalc(req, lines);
        requestMapper.updateById(req);
        lineMapper.delete(new LambdaQueryWrapper<InvoiceRequestLine>()
                .eq(InvoiceRequestLine::getRequestId, id));
        for (InvoiceRequestLine l : lines) {
            l.setId(null);
            l.setRequestId(id);
            lineMapper.insert(l);
        }
        return requestMapper.selectById(id);
    }

    @Transactional
    public InvoiceRequest transit(Long id, String action, String reason) {
        InvoiceRequest r = require(id);
        switch (action) {
            case "submit":
                mustStatus(r, "DRAFT");
                r.setStatus("SUBMITTED");
                break;
            case "approve":
                mustStatus(r, "SUBMITTED");
                r.setStatus("APPROVED");
                break;
            case "reject":
                mustStatus(r, "SUBMITTED");
                r.setStatus("REJECTED");
                r.setRejectReason(reason);
                break;
            case "cancel":
                if (!"DRAFT".equals(r.getStatus()) && !"SUBMITTED".equals(r.getStatus())) {
                    throw new BizException("仅草稿/已提交状态可撤销");
                }
                r.setStatus("CANCELLED");
                break;
            default:
                throw new BizException("不支持的操作: " + action);
        }
        requestMapper.updateById(r);
        return r;
    }

    /** 限额拆分：按行拆分为多张不超过限额的申请；单行超限报错 */
    @Transactional
    public List<InvoiceRequest> split(Long id) {
        InvoiceRequest src = require(id);
        if (!"DRAFT".equals(src.getStatus())) {
            throw new BizException("仅草稿状态可拆分");
        }
        BigDecimal limit = basicService.invoiceLimit(src.getTaxEntityId(), src.getInvoiceType());
        if (limit == null) {
            throw new BizException("该票种未配置限额，无需拆分");
        }
        List<InvoiceRequestLine> lines = linesOf(id);
        List<List<InvoiceRequestLine>> groups = new ArrayList<>();
        List<InvoiceRequestLine> cur = new ArrayList<>();
        BigDecimal curSum = BigDecimal.ZERO;
        for (InvoiceRequestLine l : lines) {
            BigDecimal lineWithTax = l.getAmount().add(l.getTaxAmount());
            if (lineWithTax.compareTo(limit) > 0) {
                throw new BizException("明细行[" + l.getGoodsName() + "]含税金额超过限额，无法拆分");
            }
            if (curSum.add(lineWithTax).compareTo(limit) > 0 && !cur.isEmpty()) {
                groups.add(cur);
                cur = new ArrayList<>();
                curSum = BigDecimal.ZERO;
            }
            cur.add(l);
            curSum = curSum.add(lineWithTax);
        }
        if (!cur.isEmpty()) {
            groups.add(cur);
        }
        if (groups.size() <= 1) {
            throw new BizException("未超过限额，无需拆分");
        }
        List<InvoiceRequest> result = new ArrayList<>();
        for (List<InvoiceRequestLine> g : groups) {
            InvoiceRequest copy = copyHeader(src);
            List<InvoiceRequestLine> newLines = new ArrayList<>();
            for (InvoiceRequestLine l : g) {
                InvoiceRequestLine nl = copyLine(l);
                newLines.add(nl);
            }
            result.add(create(copy, newLines));
        }
        // 原申请标记取消
        src.setStatus("CANCELLED");
        src.setRejectReason("已拆分为 " + groups.size() + " 张申请");
        requestMapper.updateById(src);
        return result;
    }

    /** 合并：同买方、同票种、同主体的多张 DRAFT 申请合并为一张 */
    @Transactional
    public InvoiceRequest merge(List<Long> ids) {
        if (ids == null || ids.size() < 2) {
            throw new BizException("合并至少需要两张申请");
        }
        List<InvoiceRequest> reqs = new ArrayList<>();
        for (Long id : ids) {
            InvoiceRequest r = require(id);
            mustStatus(r, "DRAFT");
            reqs.add(r);
        }
        InvoiceRequest first = reqs.get(0);
        for (InvoiceRequest r : reqs) {
            if (!r.getTaxEntityId().equals(first.getTaxEntityId())
                    || !r.getInvoiceType().equals(first.getInvoiceType())
                    || !String.valueOf(r.getBuyerName()).equals(String.valueOf(first.getBuyerName()))
                    || !String.valueOf(r.getBuyerTaxNo()).equals(String.valueOf(first.getBuyerTaxNo()))) {
                throw new BizException("仅同买方、同票种、同主体的草稿申请可合并");
            }
        }
        InvoiceRequest merged = copyHeader(first);
        merged.setExtRef(null);
        List<InvoiceRequestLine> lines = new ArrayList<>();
        for (InvoiceRequest r : reqs) {
            for (InvoiceRequestLine l : linesOf(r.getId())) {
                lines.add(copyLine(l));
            }
        }
        InvoiceRequest created = create(merged, lines);
        for (InvoiceRequest r : reqs) {
            r.setStatus("CANCELLED");
            r.setRejectReason("已合并至 " + created.getRequestNo());
            requestMapper.updateById(r);
        }
        return created;
    }

    public InvoiceRequest require(Long id) {
        InvoiceRequest r = requestMapper.selectById(id);
        if (r == null) {
            throw new BizException("开票申请不存在: " + id);
        }
        return r;
    }

    static void mustStatus(InvoiceRequest r, String expect) {
        if (!expect.equals(r.getStatus())) {
            throw new BizException("当前状态 " + r.getStatus() + " 不允许该操作，要求 " + expect);
        }
    }

    private static InvoiceRequest copyHeader(InvoiceRequest src) {
        InvoiceRequest c = new InvoiceRequest();
        c.setTaxEntityId(src.getTaxEntityId());
        c.setCustomerId(src.getCustomerId());
        c.setInvoiceType(src.getInvoiceType());
        c.setBuyerName(src.getBuyerName());
        c.setBuyerTaxNo(src.getBuyerTaxNo());
        c.setBuyerAddressPhone(src.getBuyerAddressPhone());
        c.setBuyerBank(src.getBuyerBank());
        c.setRemark(src.getRemark());
        c.setSource(src.getSource());
        c.setExtRef(src.getExtRef());
        return c;
    }

    private static InvoiceRequestLine copyLine(InvoiceRequestLine l) {
        InvoiceRequestLine c = new InvoiceRequestLine();
        c.setGoodsId(l.getGoodsId());
        c.setGoodsName(l.getGoodsName());
        c.setTaxCategoryCode(l.getTaxCategoryCode());
        c.setSpec(l.getSpec());
        c.setUnit(l.getUnit());
        c.setQuantity(l.getQuantity());
        c.setUnitPrice(l.getUnitPrice());
        c.setAmount(l.getAmount());
        c.setTaxRate(l.getTaxRate());
        c.setTaxAmount(l.getTaxAmount());
        c.setPriceIncludeTax(l.getPriceIncludeTax());
        return c;
    }
}
