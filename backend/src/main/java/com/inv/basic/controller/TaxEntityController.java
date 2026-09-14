package com.inv.basic.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inv.basic.entity.TaxEntity;
import com.inv.basic.entity.TaxEntityLimit;
import com.inv.basic.mapper.TaxEntityLimitMapper;
import com.inv.basic.mapper.TaxEntityMapper;
import com.inv.basic.service.BasicService;
import com.inv.common.BaseCrudController;
import com.inv.common.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/basic/tax-entity")
public class TaxEntityController extends BaseCrudController<TaxEntity, TaxEntityMapper> {
    @Autowired
    private BasicService basicService;
    @Autowired
    private TaxEntityLimitMapper limitMapper;

    public TaxEntityController() {
        super(TaxEntity.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"code", "name", "tax_no"};
    }

    @Override
    protected void beforeSave(TaxEntity entity) {
        basicService.validateTaxEntity(entity);
    }

    /** 主体各票种开票限额 */
    @GetMapping("/{id}/limits")
    public R<List<TaxEntityLimit>> limits(@PathVariable Long id) {
        return R.ok(limitMapper.selectList(new LambdaQueryWrapper<TaxEntityLimit>()
                .eq(TaxEntityLimit::getTaxEntityId, id)));
    }

    @PostMapping("/{id}/limits")
    public R<TaxEntityLimit> saveLimit(@PathVariable Long id, @RequestBody TaxEntityLimit limit) {
        limit.setId(null);
        limit.setTaxEntityId(id);
        if (limit.getMaxAmount() == null || limit.getMaxAmount().signum() <= 0) {
            throw new com.inv.common.BizException("限额必须为正数");
        }
        limitMapper.insert(limit);
        return R.ok(limit);
    }

    @DeleteMapping("/limits/{limitId}")
    public R<Void> deleteLimit(@PathVariable Long limitId) {
        limitMapper.deleteById(limitId);
        return R.ok();
    }
}
