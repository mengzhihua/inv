package com.inv.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.inv.basic.entity.TaxEntity;
import com.inv.basic.service.BasicService;
import com.inv.basic.service.InvoiceStockService;
import com.inv.common.BizException;
import com.inv.common.CheckCodes;
import com.inv.common.CodeGenerator;
import com.inv.common.TaxCalc;
import com.inv.integration.entity.Archive;
import com.inv.integration.mapper.ArchiveMapper;
import com.inv.sales.entity.DeliveryLog;
import com.inv.sales.entity.Invoice;
import com.inv.sales.entity.InvoiceEvent;
import com.inv.sales.entity.InvoiceLine;
import com.inv.sales.entity.InvoiceRequest;
import com.inv.sales.entity.InvoiceRequestLine;
import com.inv.sales.entity.RedInfo;
import com.inv.sales.entity.RedInfoLine;
import com.inv.sales.mapper.DeliveryLogMapper;
import com.inv.sales.mapper.InvoiceEventMapper;
import com.inv.sales.mapper.InvoiceLineMapper;
import com.inv.sales.mapper.InvoiceMapper;
import com.inv.sales.mapper.InvoiceRequestLineMapper;
import com.inv.sales.mapper.InvoiceRequestMapper;
import com.inv.sales.mapper.RedInfoLineMapper;
import com.inv.sales.mapper.RedInfoMapper;
import com.inv.system.auth.CurrentUser;
import com.inv.system.entity.User;
import com.inv.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 发票开具 / 作废 / 红冲 / 交付 */
@Service
@RequiredArgsConstructor
public class InvoiceService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvoiceMapper invoiceMapper;
    private final InvoiceLineMapper lineMapper;
    private final InvoiceEventMapper eventMapper;
    private final InvoiceRequestMapper requestMapper;
    private final InvoiceRequestLineMapper requestLineMapper;
    private final RedInfoMapper redInfoMapper;
    private final RedInfoLineMapper redInfoLineMapper;
    private final DeliveryLogMapper deliveryLogMapper;
    private final ArchiveMapper archiveMapper;
    private final InvoiceStockService stockService;
    private final InvoiceRequestService requestService;
    private final BasicService basicService;
    private final TaxService taxService;
    private final JdbcTemplate jdbc;

    @Value("${inv.mock.tax-fail:false}")
    private boolean mockTaxFail;

    private static String operator() {
        User u = CurrentUser.get();
        return u == null ? "system" : u.getUsername();
    }

    // ==================== 开票 ====================
    @Transactional
    public Invoice issue(Long requestId) {
        // 原子占用：仅 APPROVED 且未开票的申请可进入开票流程，异常回滚自动恢复 APPROVED
        int claimed = jdbc.update("UPDATE inv_invoice_request SET status = 'ISSUING', updated_at = CURRENT_TIMESTAMP"
                + " WHERE id = ? AND status = 'APPROVED' AND invoice_id IS NULL", requestId);
        if (claimed != 1) {
            throw new BizException("申请不可开票或正在开票");
        }
        InvoiceRequest req = requestService.require(requestId);
        // 模拟税控失败：含税金额尾数 .99
        if (mockTaxFail && req.getTotalWithTax() != null
                && req.getTotalWithTax().remainder(BigDecimal.ONE).movePointRight(2).intValue() == 99) {
            throw new BizException("模拟税控失败：开票请求被税局拒绝（INV_MOCK_TAX_FAIL）");
        }
        TaxEntity entity = basicService.requireTaxEntity(req.getTaxEntityId());

        Invoice inv = new Invoice();
        inv.setInvoiceType(req.getInvoiceType());
        inv.setTaxEntityId(req.getTaxEntityId());
        inv.setBuyerName(req.getBuyerName());
        inv.setBuyerTaxNo(req.getBuyerTaxNo());
        inv.setBuyerAddressPhone(req.getBuyerAddressPhone());
        inv.setBuyerBank(req.getBuyerBank());
        inv.setSellerName(entity.getName());
        inv.setSellerTaxNo(entity.getTaxNo());
        inv.setSellerAddressPhone(trim((entity.getAddress() == null ? "" : entity.getAddress()) + " "
                + (entity.getPhone() == null ? "" : entity.getPhone())));
        inv.setSellerBank(trim((entity.getBankName() == null ? "" : entity.getBankName()) + " "
                + (entity.getBankAccount() == null ? "" : entity.getBankAccount())));
        inv.setIssueDate(LocalDate.now());
        inv.setTotalAmount(req.getTotalAmount());
        inv.setTotalTax(req.getTotalTax());
        inv.setTotalWithTax(req.getTotalWithTax());
        inv.setStatus("ISSUED");
        inv.setRequestId(req.getId());
        inv.setMachineNo("MACH-" + entity.getCode());
        inv.setDrawer(entity.getDrawer());
        inv.setPayee(entity.getPayee());
        inv.setReviewer(entity.getReviewer());
        inv.setDeliveryStatus("UNDELIVERED");
        inv.setRedAmount(BigDecimal.ZERO);
        inv.setRedTax(BigDecimal.ZERO);

        if ("ALL_ELECTRIC".equals(req.getInvoiceType())) {
            inv.setInvoiceCode(null);
            inv.setInvoiceNo(nextElectricNo());
        } else {
            InvoiceStockService.IssuedNo no = stockService.takeNumber(req.getTaxEntityId(), req.getInvoiceType());
            inv.setInvoiceCode(no.getInvoiceCode());
            inv.setInvoiceNo(no.getInvoiceNo());
        }
        inv.setCheckCode(CheckCodes.derive(inv.getInvoiceCode(), inv.getInvoiceNo(),
                inv.getIssueDate(), inv.getTotalAmount()));
        inv.setPdfUrl("/archive/" + inv.getInvoiceNo() + ".pdf");
        try {
            invoiceMapper.insert(inv);
        } catch (DuplicateKeyException e) {
            throw new BizException("发票号码冲突，请重试");
        }

        for (InvoiceRequestLine rl : requestService.linesOf(req.getId())) {
            InvoiceLine l = new InvoiceLine();
            l.setInvoiceId(inv.getId());
            l.setGoodsName(rl.getGoodsName());
            l.setTaxCategoryCode(rl.getTaxCategoryCode());
            l.setSpec(rl.getSpec());
            l.setUnit(rl.getUnit());
            l.setQuantity(rl.getQuantity());
            l.setUnitPrice(rl.getUnitPrice());
            l.setAmount(rl.getAmount());
            l.setTaxRate(rl.getTaxRate());
            l.setTaxAmount(rl.getTaxAmount());
            lineMapper.insert(l);
        }
        req.setStatus("ISSUED");
        req.setInvoiceId(inv.getId());
        requestMapper.updateById(req);
        event(inv.getId(), "ISSUED", "开具 " + inv.getInvoiceType() + " " + inv.getInvoiceNo());
        archive(inv);
        return inv;
    }

    /** 数电票号码：20 位（4 位年份 + 16 位年内递增序号，与 CodeGenerator 相同的 UPDATE/INSERT 模式） */
    private String nextElectricNo() {
        String year = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
        int updated = jdbc.update("UPDATE inv_sequence SET seq_value = seq_value + 1 WHERE prefix = 'EINV' AND day_key = ?", year);
        if (updated == 0) {
            try {
                jdbc.update("INSERT INTO inv_sequence (prefix, day_key, seq_value) VALUES ('EINV', ?, 1)", year);
            } catch (DuplicateKeyException e) {
                jdbc.update("UPDATE inv_sequence SET seq_value = seq_value + 1 WHERE prefix = 'EINV' AND day_key = ?", year);
            }
        }
        Integer n = jdbc.queryForObject("SELECT seq_value FROM inv_sequence WHERE prefix = 'EINV' AND day_key = ?", Integer.class, year);
        return String.format("%s%016d", year, n);
    }

    // ==================== 作废 ====================
    @Transactional
    public Invoice cancel(Long invoiceId, String reason) {
        Invoice inv = invoiceMapper.selectForUpdate(invoiceId);
        if (inv == null) {
            throw new BizException("发票不存在: " + invoiceId);
        }
        if (!"SPECIAL".equals(inv.getInvoiceType()) && !"NORMAL".equals(inv.getInvoiceType())) {
            throw new BizException("电子发票/数电票不允许作废，请使用红冲");
        }
        if (!"ISSUED".equals(inv.getStatus())) {
            throw new BizException("仅已开出发票可作废，当前 " + inv.getStatus());
        }
        if (inv.getIssueDate() == null || !inv.getIssueDate().format(DateTimeFormatter.ofPattern("yyyy-MM"))
                .equals(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")))) {
            throw new BizException("跨月发票不允许作废，请使用红冲");
        }
        if ("DELIVERED".equals(inv.getDeliveryStatus())) {
            throw new BizException("发票已交付，不允许作废，请使用红冲");
        }
        if (inv.getRedAmount() != null && inv.getRedAmount().signum() > 0) {
            throw new BizException("发票已发生红冲，不允许作废");
        }
        taxService.requireOpen(inv.getTaxEntityId(),
                inv.getIssueDate().format(DateTimeFormatter.ofPattern("yyyy-MM")));
        inv.setStatus("CANCELLED");
        invoiceMapper.updateById(inv);
        event(inv.getId(), "CANCELLED", reason);
        return inv;
    }

    // ==================== 红冲 ====================
    /** 创建红字信息表（可部分红冲：行/金额 ≤ 原票剩余可红金额） */
    @Transactional
    public RedInfo createRedInfo(Long invoiceId, String reason, List<RedInfoLine> lines) {
        Invoice inv = requireInvoice(invoiceId);
        if (!"ISSUED".equals(inv.getStatus())) {
            throw new BizException("仅已开出（含部分红冲）发票可红冲，当前 " + inv.getStatus());
        }
        if (reason == null || reason.isEmpty()) {
            reason = "SELLER_ERROR";
        }
        List<InvoiceLine> orig = linesOf(invoiceId);
        List<RedInfoLine> use = lines;
        if (use == null || use.isEmpty()) {
            if (orig.isEmpty()) {
                throw new BizException("原票无明细行，请明确传入红冲明细");
            }
            if (inv.getRedAmount() != null && inv.getRedAmount().signum() > 0) {
                throw new BizException("原票已部分红冲，请明确传入红冲明细");
            }
            // 未给行且未发生过红冲：按原票行全额红冲
            use = new ArrayList<>();
            for (InvoiceLine l : orig) {
                RedInfoLine r = copyToRed(l);
                use.add(r);
            }
        }
        BigDecimal amount = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        for (RedInfoLine l : use) {
            if (l.getAmount() == null && l.getQuantity() != null && l.getUnitPrice() != null) {
                l.setAmount(TaxCalc.round(l.getQuantity().multiply(l.getUnitPrice())));
            }
            if (l.getAmount() == null) {
                throw new BizException("红冲明细缺少金额");
            }
            BigDecimal rate = l.getTaxRate() == null ? new BigDecimal("0.13") : l.getTaxRate();
            l.setTaxRate(rate);
            if (l.getTaxAmount() == null) {
                l.setTaxAmount(TaxCalc.taxOf(l.getAmount(), rate));
            }
            amount = amount.add(l.getAmount());
            tax = tax.add(l.getTaxAmount());
        }
        BigDecimal remainAmount = inv.getTotalAmount().subtract(null2(inv.getRedAmount()));
        BigDecimal remainTax = inv.getTotalTax().subtract(null2(inv.getRedTax()));
        if (amount.compareTo(remainAmount) > 0 || tax.compareTo(remainTax) > 0) {
            throw new BizException("红冲金额超过原票剩余可红金额（剩余不含税 " + remainAmount + "，税额 " + remainTax + "）");
        }
        RedInfo info = new RedInfo();
        info.setInvoiceId(invoiceId);
        info.setReason(reason);
        info.setTotalAmount(TaxCalc.round(amount));
        info.setTotalTax(TaxCalc.round(tax));
        info.setTotalWithTax(TaxCalc.round(amount.add(tax)));
        info.setStatus("DRAFT");
        redInfoMapper.insert(info);
        for (RedInfoLine l : use) {
            l.setId(null);
            l.setRedInfoId(info.getId());
            redInfoLineMapper.insert(l);
        }
        return info;
    }

    /** 确认红字信息表（模拟税局审核，生成 16 位编号） */
    @Transactional
    public RedInfo confirmRedInfo(Long redInfoId) {
        RedInfo info = requireRedInfo(redInfoId);
        if (!"DRAFT".equals(info.getStatus())) {
            throw new BizException("红字信息表已确认或使用");
        }
        info.setRedInfoNo(String.format("%016d", Math.abs(RANDOM.nextLong() % 10000000000000000L)));
        info.setStatus("CONFIRMED");
        redInfoMapper.updateById(info);
        return info;
    }

    /** 红冲：生成负数红字发票（RED），原票剩余可红金额减少，全额红冲后原票 RED_FLUSHED */
    @Transactional
    public Invoice redFlush(Long redInfoId) {
        RedInfo info = requireRedInfo(redInfoId);
        if (!"CONFIRMED".equals(info.getStatus())) {
            throw new BizException("红字信息表须先确认（CONFIRMED）");
        }
        // 行锁读取原票，防止多张已确认红字信息表并发红冲超额
        Invoice orig = invoiceMapper.selectForUpdate(info.getInvoiceId());
        if (orig == null) {
            throw new BizException("发票不存在: " + info.getInvoiceId());
        }
        if (!"ISSUED".equals(orig.getStatus())) {
            throw new BizException("原票状态为 " + orig.getStatus() + "，不可红冲");
        }
        // 重新校验未超过原票剩余可红金额（多张红字信息表可能先后确认）
        BigDecimal remainAmount = orig.getTotalAmount().subtract(null2(orig.getRedAmount()));
        BigDecimal remainTax = orig.getTotalTax().subtract(null2(orig.getRedTax()));
        if (info.getTotalAmount().compareTo(remainAmount) > 0 || info.getTotalTax().compareTo(remainTax) > 0) {
            throw new BizException("红冲金额超过原票剩余可红金额（剩余不含税 " + remainAmount + "，税额 " + remainTax + "）");
        }
        List<RedInfoLine> rlines = redInfoLineMapper.selectList(new LambdaQueryWrapper<RedInfoLine>()
                .eq(RedInfoLine::getRedInfoId, redInfoId));

        Invoice red = new Invoice();
        red.setInvoiceType(orig.getInvoiceType());
        red.setTaxEntityId(orig.getTaxEntityId());
        red.setBuyerName(orig.getBuyerName());
        red.setBuyerTaxNo(orig.getBuyerTaxNo());
        red.setBuyerAddressPhone(orig.getBuyerAddressPhone());
        red.setBuyerBank(orig.getBuyerBank());
        red.setSellerName(orig.getSellerName());
        red.setSellerTaxNo(orig.getSellerTaxNo());
        red.setSellerAddressPhone(orig.getSellerAddressPhone());
        red.setSellerBank(orig.getSellerBank());
        red.setIssueDate(LocalDate.now());
        red.setTotalAmount(info.getTotalAmount().negate());
        red.setTotalTax(info.getTotalTax().negate());
        red.setTotalWithTax(info.getTotalWithTax().negate());
        red.setStatus("RED");
        red.setRedOfInvoiceId(orig.getId());
        red.setRedInfoId(info.getId());
        red.setMachineNo(orig.getMachineNo());
        red.setDrawer(orig.getDrawer());
        red.setPayee(orig.getPayee());
        red.setReviewer(orig.getReviewer());
        red.setDeliveryStatus("UNDELIVERED");
        red.setRedAmount(BigDecimal.ZERO);
        red.setRedTax(BigDecimal.ZERO);
        if ("ALL_ELECTRIC".equals(orig.getInvoiceType())) {
            red.setInvoiceNo(nextElectricNo());
        } else {
            InvoiceStockService.IssuedNo no = stockService.takeNumber(orig.getTaxEntityId(), orig.getInvoiceType());
            red.setInvoiceCode(no.getInvoiceCode());
            red.setInvoiceNo(no.getInvoiceNo());
        }
        red.setCheckCode(CheckCodes.derive(red.getInvoiceCode(), red.getInvoiceNo(), red.getIssueDate(), red.getTotalAmount()));
        red.setPdfUrl("/archive/" + red.getInvoiceNo() + ".pdf");
        invoiceMapper.insert(red);
        for (RedInfoLine rl : rlines) {
            InvoiceLine l = new InvoiceLine();
            l.setInvoiceId(red.getId());
            l.setGoodsName(rl.getGoodsName());
            l.setTaxCategoryCode(rl.getTaxCategoryCode());
            l.setSpec(rl.getSpec());
            l.setUnit(rl.getUnit());
            l.setQuantity(rl.getQuantity() == null ? null : rl.getQuantity().negate());
            l.setUnitPrice(rl.getUnitPrice());
            l.setAmount(rl.getAmount().negate());
            l.setTaxRate(rl.getTaxRate());
            l.setTaxAmount(rl.getTaxAmount().negate());
            lineMapper.insert(l);
        }
        // 原票累计已红
        orig.setRedAmount(null2(orig.getRedAmount()).add(info.getTotalAmount()));
        orig.setRedTax(null2(orig.getRedTax()).add(info.getTotalTax()));
        if (orig.getRedAmount().compareTo(orig.getTotalAmount()) >= 0) {
            orig.setStatus("RED_FLUSHED");
        }
        invoiceMapper.updateById(orig);
        int used = jdbc.update("UPDATE inv_red_info SET status = 'USED', updated_at = CURRENT_TIMESTAMP"
                + " WHERE id = ? AND status = 'CONFIRMED'", redInfoId);
        if (used != 1) {
            throw new BizException("红字信息表状态已变化，请刷新重试");
        }
        event(orig.getId(), "RED_FLUSH", "红冲 " + info.getTotalWithTax() + "，红字票 " + red.getInvoiceNo());
        event(red.getId(), "ISSUED", "红字发票，原票 " + orig.getInvoiceNo());
        archive(red);
        return red;
    }

    // ==================== 交付 ====================
    @Transactional
    public DeliveryLog deliver(Long invoiceId, String channel, String target) {
        Invoice inv = requireInvoice(invoiceId);
        if (!"ISSUED".equals(inv.getStatus()) && !"RED".equals(inv.getStatus())) {
            throw new BizException("仅有效发票可交付");
        }
        if (!"EMAIL".equals(channel) && !"SMS".equals(channel)) {
            throw new BizException("交付渠道仅支持 EMAIL/SMS");
        }
        if (target == null || target.isEmpty()) {
            throw new BizException("交付目标（邮箱/手机号）不能为空");
        }
        DeliveryLog log = new DeliveryLog();
        log.setInvoiceId(invoiceId);
        log.setChannel(channel);
        log.setTarget(target);
        log.setStatus("SUCCESS");
        deliveryLogMapper.insert(log);
        inv.setDeliveryStatus("DELIVERED");
        invoiceMapper.updateById(inv);
        event(invoiceId, "DELIVERED", channel + " -> " + target);
        return log;
    }

    // ==================== 查询 ====================
    public Invoice requireInvoice(Long id) {
        Invoice inv = invoiceMapper.selectById(id);
        if (inv == null) {
            throw new BizException("发票不存在: " + id);
        }
        return inv;
    }

    public RedInfo requireRedInfo(Long id) {
        RedInfo i = redInfoMapper.selectById(id);
        if (i == null) {
            throw new BizException("红字信息表不存在: " + id);
        }
        return i;
    }

    public List<InvoiceLine> linesOf(Long invoiceId) {
        return lineMapper.selectList(new LambdaQueryWrapper<InvoiceLine>()
                .eq(InvoiceLine::getInvoiceId, invoiceId).orderByAsc(InvoiceLine::getId));
    }

    public List<InvoiceEvent> eventsOf(Long invoiceId) {
        return eventMapper.selectList(new LambdaQueryWrapper<InvoiceEvent>()
                .eq(InvoiceEvent::getInvoiceId, invoiceId).orderByAsc(InvoiceEvent::getId));
    }

    public List<DeliveryLog> deliveriesOf(Long invoiceId) {
        return deliveryLogMapper.selectList(new LambdaQueryWrapper<DeliveryLog>()
                .eq(DeliveryLog::getInvoiceId, invoiceId).orderByDesc(DeliveryLog::getId));
    }

    private void event(Long invoiceId, String event, String detail) {
        InvoiceEvent e = new InvoiceEvent();
        e.setInvoiceId(invoiceId);
        e.setEvent(event);
        e.setDetail(detail);
        e.setOperator(operator());
        e.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(e);
    }

    private void archive(Invoice inv) {
        Archive a = new Archive();
        a.setInvoiceId(inv.getId());
        a.setInvoiceNo(inv.getInvoiceNo());
        a.setInvoiceType(inv.getInvoiceType());
        a.setDirection("OUT");
        a.setIssueMonth(inv.getIssueDate().format(DateTimeFormatter.ofPattern("yyyy-MM")));
        a.setPdfUrl(inv.getPdfUrl());
        a.setOfdUrl("/archive/" + inv.getInvoiceNo() + ".ofd");
        a.setMeta(inv.getSellerName() + " -> " + inv.getBuyerName() + " " + inv.getTotalWithTax());
        archiveMapper.insert(a);
    }

    private static BigDecimal null2(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trim(String s) {
        return s.trim().isEmpty() ? null : s.trim();
    }

    private static RedInfoLine copyToRed(InvoiceLine l) {
        RedInfoLine r = new RedInfoLine();
        r.setGoodsName(l.getGoodsName());
        r.setTaxCategoryCode(l.getTaxCategoryCode());
        r.setSpec(l.getSpec());
        r.setUnit(l.getUnit());
        r.setQuantity(l.getQuantity());
        r.setUnitPrice(l.getUnitPrice());
        r.setAmount(l.getAmount());
        r.setTaxRate(l.getTaxRate());
        r.setTaxAmount(l.getTaxAmount());
        return r;
    }
}
