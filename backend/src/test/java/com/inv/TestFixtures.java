package com.inv;

import com.inv.basic.entity.InvoiceStock;
import com.inv.basic.entity.TaxEntity;
import com.inv.basic.entity.TaxEntityLimit;
import com.inv.basic.mapper.InvoiceStockMapper;
import com.inv.basic.mapper.TaxEntityLimitMapper;
import com.inv.basic.mapper.TaxEntityMapper;
import com.inv.sales.entity.InvoiceRequest;
import com.inv.sales.entity.InvoiceRequestLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** 测试夹具：自建纳税主体/号段，避免与 data.sql 演示数据相互干扰 */
@Component
public class TestFixtures {
    @Autowired
    TaxEntityMapper taxEntityMapper;
    @Autowired
    TaxEntityLimitMapper limitMapper;
    @Autowired
    InvoiceStockMapper stockMapper;

    public TaxEntity newEntity(String code, String taxNo) {
        TaxEntity e = new TaxEntity();
        e.setCode(code);
        e.setName("测试主体" + code);
        e.setTaxNo(taxNo);
        e.setTaxpayerType("GENERAL");
        e.setStatus(1);
        e.setDrawer("测试开票");
        e.setPayee("测试收款");
        e.setReviewer("测试复核");
        taxEntityMapper.insert(e);
        return e;
    }

    public void limit(Long entityId, String type, String max) {
        TaxEntityLimit l = new TaxEntityLimit();
        l.setTaxEntityId(entityId);
        l.setInvoiceType(type);
        l.setMaxAmount(new BigDecimal(max));
        limitMapper.insert(l);
    }

    public InvoiceStock stock(Long entityId, String type, String code, String start, String end) {
        InvoiceStock s = new InvoiceStock();
        s.setTaxEntityId(entityId);
        s.setInvoiceType(type);
        s.setInvoiceCode(code);
        s.setStartNo(start);
        s.setEndNo(end);
        s.setCurrentNo(start);
        s.setRemaining(Integer.parseInt(end) - Integer.parseInt(start) + 1);
        s.setStatus(1);
        stockMapper.insert(s);
        return s;
    }

    public static InvoiceRequest req(Long entityId, String type) {
        InvoiceRequest r = new InvoiceRequest();
        r.setTaxEntityId(entityId);
        r.setInvoiceType(type);
        r.setBuyerName("测试买方公司");
        r.setBuyerTaxNo("91310000TEST0000X1");
        r.setBuyerAddressPhone("测试地址 021-00000000");
        r.setBuyerBank("测试银行 123456");
        r.setSource("MANUAL");
        return r;
    }

    public static InvoiceRequestLine line(String name, String qty, String price, String amount, String rate) {
        InvoiceRequestLine l = new InvoiceRequestLine();
        l.setGoodsName(name);
        l.setQuantity(qty == null ? null : new BigDecimal(qty));
        l.setUnitPrice(price == null ? null : new BigDecimal(price));
        l.setAmount(amount == null ? null : new BigDecimal(amount));
        l.setTaxRate(new BigDecimal(rate));
        return l;
    }

    public static List<InvoiceRequestLine> one(String amount, String rate) {
        return java.util.Collections.singletonList(line("测试商品", null, null, amount, rate));
    }
}
