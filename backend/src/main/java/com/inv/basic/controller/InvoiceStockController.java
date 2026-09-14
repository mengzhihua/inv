package com.inv.basic.controller;

import com.inv.basic.entity.InvoiceStock;
import com.inv.basic.mapper.InvoiceStockMapper;
import com.inv.common.BaseCrudController;
import com.inv.common.BizException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/invoice-stock")
public class InvoiceStockController extends BaseCrudController<InvoiceStock, InvoiceStockMapper> {
    public InvoiceStockController() {
        super(InvoiceStock.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"invoice_code"};
    }

    @Override
    protected void beforeSave(InvoiceStock s) {
        if (s.getId() == null) {
            if (s.getStartNo() == null || s.getEndNo() == null || s.getStartNo().compareTo(s.getEndNo()) > 0) {
                throw new BizException("号段起止号码不正确");
            }
            if (s.getInvoiceCode() != null && !s.getInvoiceCode().matches("^\\d{10}$|^\\d{12}$")) {
                throw new BizException("发票代码须为 10/12 位数字");
            }
            s.setCurrentNo(s.getStartNo());
            try {
                int total = Integer.parseInt(s.getEndNo()) - Integer.parseInt(s.getStartNo()) + 1;
                s.setRemaining(total);
            } catch (NumberFormatException e) {
                throw new BizException("号段号码须为数字");
            }
        }
        if (s.getStatus() == null) {
            s.setStatus(1);
        }
    }
}
