package com.inv.basic.controller;

import com.inv.basic.entity.Partner;
import com.inv.basic.mapper.PartnerMapper;
import com.inv.basic.service.BasicService;
import com.inv.common.BaseCrudController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/partner")
public class PartnerController extends BaseCrudController<Partner, PartnerMapper> {
    @Autowired
    private BasicService basicService;

    public PartnerController() {
        super(Partner.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"name", "tax_no"};
    }

    @Override
    protected void beforeSave(Partner entity) {
        basicService.validatePartner(entity);
    }
}
