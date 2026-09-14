package com.inv.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inv.sales.entity.Invoice;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InvoiceMapper extends BaseMapper<Invoice> {

    /** 行锁读取（须在事务内使用），供红冲等并发写场景使用 */
    @Select("SELECT * FROM inv_invoice WHERE id = #{id} FOR UPDATE")
    Invoice selectForUpdate(@Param("id") Long id);
}
