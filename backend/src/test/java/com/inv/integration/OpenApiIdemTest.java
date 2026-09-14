package com.inv.integration;

import com.inv.TestFixtures;
import com.inv.basic.entity.TaxEntity;
import com.inv.sales.entity.InvoiceRequest;
import com.inv.sales.service.InvoiceRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** Open API 幂等：同 source+extRef 重复推送返回同一申请 */
@SpringBootTest
@ActiveProfiles("test")
class OpenApiIdemTest {
    @Autowired
    InvoiceRequestService requestService;
    @Autowired
    TestFixtures fx;

    @Test
    void idempotentBySourceExtRef() {
        TaxEntity e = fx.newEntity("O-IDEM", "91310000OIDEM0001");
        InvoiceRequest r = TestFixtures.req(e.getId(), "E_NORMAL");
        r.setSource("OMS");
        r.setExtRef("ORDER-123");
        InvoiceRequest a = requestService.create(r, TestFixtures.one("100.00", "0.13"));

        InvoiceRequest r2 = TestFixtures.req(e.getId(), "E_NORMAL");
        r2.setSource("OMS");
        r2.setExtRef("ORDER-123");
        r2.setBuyerName("另一家买方"); // 即使内容不同也应返回原单
        InvoiceRequest b = requestService.create(r2, TestFixtures.one("200.00", "0.13"));
        assertEquals(a.getId(), b.getId());
        assertEquals(a.getRequestNo(), b.getRequestNo());
    }
}
