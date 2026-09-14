package com.inv.basic.controller;

import com.inv.basic.entity.Goods;
import com.inv.basic.mapper.GoodsMapper;
import com.inv.basic.service.BasicService;
import com.inv.common.BaseCrudController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/goods")
public class GoodsController extends BaseCrudController<Goods, GoodsMapper> {
    @Autowired
    private BasicService basicService;

    public GoodsController() {
        super(Goods.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"code", "name", "tax_category_code"};
    }

    @Override
    protected void beforeSave(Goods entity) {
        basicService.validateGoods(entity);
    }
}
