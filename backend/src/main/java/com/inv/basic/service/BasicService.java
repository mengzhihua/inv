package com.inv.basic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inv.basic.entity.Goods;
import com.inv.basic.entity.InvoiceStock;
import com.inv.basic.entity.Partner;
import com.inv.basic.entity.TaxEntity;
import com.inv.basic.entity.TaxEntityLimit;
import com.inv.basic.mapper.GoodsMapper;
import com.inv.basic.mapper.InvoiceStockMapper;
import com.inv.basic.mapper.PartnerMapper;
import com.inv.basic.mapper.TaxEntityLimitMapper;
import com.inv.basic.mapper.TaxEntityMapper;
import com.inv.common.BizException;
import com.inv.common.TaxCalc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/** 基础数据校验与查询 */
@Service
@RequiredArgsConstructor
public class BasicService {
    private static final BigDecimal[] ALLOWED_RATES = {
            new BigDecimal("0"), new BigDecimal("0.01"), new BigDecimal("0.03"), new BigDecimal("0.05"),
            new BigDecimal("0.06"), new BigDecimal("0.09"), new BigDecimal("0.13")};

    private final TaxEntityMapper taxEntityMapper;
    private final TaxEntityLimitMapper limitMapper;
    private final PartnerMapper partnerMapper;
    private final GoodsMapper goodsMapper;
    private final InvoiceStockMapper stockMapper;

    public TaxEntity requireTaxEntity(Long id) {
        TaxEntity e = taxEntityMapper.selectById(id);
        if (e == null || e.getStatus() == null || e.getStatus() != 1) {
            throw new BizException("纳税主体不存在或已停用: " + id);
        }
        return e;
    }

    /** 主体某票种的单张开票限额；未配置视为不限 */
    public BigDecimal invoiceLimit(Long taxEntityId, String invoiceType) {
        TaxEntityLimit l = limitMapper.selectOne(new LambdaQueryWrapper<TaxEntityLimit>()
                .eq(TaxEntityLimit::getTaxEntityId, taxEntityId)
                .eq(TaxEntityLimit::getInvoiceType, invoiceType));
        return l == null ? null : l.getMaxAmount();
    }

    public void validateTaxEntity(TaxEntity e) {
        if (!TaxCalc.validTaxNo(e.getTaxNo())) {
            throw new BizException("纳税人识别号格式不正确（15/17/18/20 位）");
        }
        if (e.getTaxpayerType() == null) {
            e.setTaxpayerType("GENERAL");
        }
        if (e.getStatus() == null) {
            e.setStatus(1);
        }
    }

    public void validatePartner(Partner p) {
        if (p.getTaxNo() != null && !p.getTaxNo().isEmpty() && !TaxCalc.validTaxNo(p.getTaxNo())) {
            throw new BizException("往来单位纳税人识别号格式不正确");
        }
        if (p.getStatus() == null) {
            p.setStatus(1);
        }
    }

    public void validateGoods(Goods g) {
        if (g.getTaxCategoryCode() != null && !g.getTaxCategoryCode().isEmpty()
                && !g.getTaxCategoryCode().matches("^\\d{19}$")) {
            throw new BizException("税收分类编码须为 19 位数字");
        }
        BigDecimal rate = g.getTaxRate() == null ? new BigDecimal("0.13") : g.getTaxRate();
        boolean ok = false;
        for (BigDecimal r : ALLOWED_RATES) {
            if (r.compareTo(rate) == 0) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            throw new BizException("税率必须为 0/0.01/0.03/0.05/0.06/0.09/0.13 之一");
        }
        g.setTaxRate(rate);
        if (g.getStatus() == null) {
            g.setStatus(1);
        }
    }

    public Partner findPartnerByTaxNo(String taxNo) {
        if (taxNo == null || taxNo.isEmpty()) {
            return null;
        }
        return partnerMapper.selectOne(new LambdaQueryWrapper<Partner>().eq(Partner::getTaxNo, taxNo));
    }

    public TaxEntity findEntityByTaxNo(String taxNo) {
        if (taxNo == null || taxNo.isEmpty()) {
            return null;
        }
        return taxEntityMapper.selectOne(new LambdaQueryWrapper<TaxEntity>().eq(TaxEntity::getTaxNo, taxNo));
    }

    public List<TaxEntity> listEntities() {
        return taxEntityMapper.selectList(new LambdaQueryWrapper<TaxEntity>().eq(TaxEntity::getStatus, 1));
    }

    public InvoiceStock findStock(Long taxEntityId, String invoiceType) {
        return stockMapper.selectOne(new LambdaQueryWrapper<InvoiceStock>()
                .eq(InvoiceStock::getTaxEntityId, taxEntityId)
                .eq(InvoiceStock::getInvoiceType, invoiceType)
                .eq(InvoiceStock::getStatus, 1)
                .orderByAsc(InvoiceStock::getId)
                .last("LIMIT 1"));
    }
}
