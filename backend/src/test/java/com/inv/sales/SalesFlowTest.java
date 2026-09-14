package com.inv.sales;

import com.inv.TestFixtures;
import com.inv.basic.entity.InvoiceStock;
import com.inv.basic.entity.TaxEntity;
import com.inv.basic.service.InvoiceStockService;
import com.inv.common.BizException;
import com.inv.common.TaxCalc;
import com.inv.sales.entity.Invoice;
import com.inv.sales.entity.InvoiceRequest;
import com.inv.sales.entity.InvoiceRequestLine;
import com.inv.sales.entity.RedInfo;
import com.inv.sales.entity.RedInfoLine;
import com.inv.sales.service.InvoiceRequestService;
import com.inv.sales.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SalesFlowTest {
    @Autowired
    InvoiceRequestService requestService;
    @Autowired
    InvoiceService invoiceService;
    @Autowired
    InvoiceStockService stockService;
    @Autowired
    TestFixtures fx;
    @Autowired
    PlatformTransactionManager txManager;

    @Test
    void taxCalcAndTolerance() {
        assertEquals(new BigDecimal("130.00"), TaxCalc.taxOf(new BigDecimal("1000"), new BigDecimal("0.13")));
        assertTrue(TaxCalc.withinTolerance(new BigDecimal("100.05"), new BigDecimal("100.00")));
        assertFalse(TaxCalc.withinTolerance(new BigDecimal("100.10"), new BigDecimal("100.00")));
        // 声明税额偏差超过容差 → 报错
        TaxEntity e = fx.newEntity("T-CALC", "91310000TESTCALC01");
        InvoiceRequest r = TestFixtures.req(e.getId(), "E_NORMAL");
        InvoiceRequestLine l = TestFixtures.line("商品", null, null, "1000.00", "0.13");
        l.setTaxAmount(new BigDecimal("200.00"));
        List<InvoiceRequestLine> lines = Collections.singletonList(l);
        BizException ex = assertThrows(BizException.class, () -> requestService.create(r, lines));
        assertTrue(ex.getMessage().contains("税额"));
    }

    @Test
    void includeTaxBackCalc() {
        BigDecimal[] back = TaxCalc.fromIncludeTax(new BigDecimal("1130.00"), new BigDecimal("0.13"));
        assertEquals(new BigDecimal("1000.00"), back[0]);
        assertEquals(new BigDecimal("130.00"), back[1]);

        TaxEntity e = fx.newEntity("T-INC", "91310000TESTINC0A1");
        InvoiceRequest r = TestFixtures.req(e.getId(), "E_NORMAL");
        InvoiceRequestLine l = TestFixtures.line("商品", "1", null, "1130.00", "0.13");
        l.setPriceIncludeTax(1);
        InvoiceRequest saved = requestService.create(r, Collections.singletonList(l));
        assertEquals(new BigDecimal("1000.00"), saved.getTotalAmount());
        assertEquals(new BigDecimal("130.00"), saved.getTotalTax());
        InvoiceRequestLine sl = requestService.linesOf(saved.getId()).get(0);
        assertEquals(new BigDecimal("1000.00"), sl.getAmount());
        assertEquals(new BigDecimal("130.00"), sl.getTaxAmount());
    }

    @Test
    void specialInvoiceRequiredFields() {
        TaxEntity e = fx.newEntity("T-SP", "91310000TESTSPC01");
        InvoiceRequest r = TestFixtures.req(e.getId(), "SPECIAL");
        r.setBuyerTaxNo(null);
        BizException ex = assertThrows(BizException.class,
                () -> requestService.create(r, TestFixtures.one("100.00", "0.13")));
        assertTrue(ex.getMessage().contains("税号"));

        // 普票个人可空
        InvoiceRequest p = TestFixtures.req(e.getId(), "NORMAL");
        p.setBuyerName("个人消费者");
        p.setBuyerTaxNo(null);
        InvoiceRequest saved = requestService.create(p, TestFixtures.one("50.00", "0.13"));
        assertEquals("DRAFT", saved.getStatus());
    }

    @Autowired
    com.inv.basic.mapper.TaxEntityLimitMapper limitMapper;

    private void updateLimit(Long entityId, String type, String max) {
        com.inv.basic.entity.TaxEntityLimit l = limitMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.inv.basic.entity.TaxEntityLimit>()
                        .eq(com.inv.basic.entity.TaxEntityLimit::getTaxEntityId, entityId)
                        .eq(com.inv.basic.entity.TaxEntityLimit::getInvoiceType, type));
        l.setMaxAmount(new BigDecimal(max));
        limitMapper.updateById(l);
    }

    @Test
    void limitSplit() {
        TaxEntity e = fx.newEntity("T-SPLIT", "91310000TESTSPLT1");
        fx.limit(e.getId(), "E_NORMAL", "100000");
        fx.stock(e.getId(), "E_NORMAL", "999000000001", "10000001", "10000010");

        // 建单：3 行各含税 565（500*1.13），合计 1695
        List<InvoiceRequestLine> big = new ArrayList<>();
        big.add(TestFixtures.line("A", null, null, "500.00", "0.13"));
        big.add(TestFixtures.line("B", null, null, "500.00", "0.13"));
        big.add(TestFixtures.line("C", null, null, "500.00", "0.13"));
        InvoiceRequest created = requestService.create(TestFixtures.req(e.getId(), "E_NORMAL"), big);

        // 收紧限额至 1200：565+565=1130 一组，第三个 565 另起一张 → 拆为 2 张
        updateLimit(e.getId(), "E_NORMAL", "1200");
        List<InvoiceRequest> parts = requestService.split(created.getId());
        assertEquals(2, parts.size());
        for (InvoiceRequest p : parts) {
            assertTrue(p.getTotalWithTax().compareTo(new BigDecimal("1200")) <= 0);
            assertEquals("DRAFT", p.getStatus());
        }
        assertEquals("CANCELLED", requestService.require(created.getId()).getStatus());

        // 超限额创建直接被拒
        updateLimit(e.getId(), "E_NORMAL", "500");
        assertThrows(BizException.class, () -> requestService.create(
                TestFixtures.req(e.getId(), "E_NORMAL"), TestFixtures.one("500.00", "0.13")));

        // 单行超限无法拆分：先在限额高时建 565 行，再收紧到 500
        updateLimit(e.getId(), "E_NORMAL", "100000");
        InvoiceRequest c3 = requestService.create(TestFixtures.req(e.getId(), "E_NORMAL"),
                TestFixtures.one("500.00", "0.13"));
        updateLimit(e.getId(), "E_NORMAL", "500");
        BizException ex = assertThrows(BizException.class, () -> requestService.split(c3.getId()));
        assertTrue(ex.getMessage().contains("无法拆分"));
    }

    @Test
    void concurrentTakeNumberNoDup() throws Exception {
        TaxEntity e = fx.newEntity("T-CC", "91310000TESTCONC01");
        fx.stock(e.getId(), "NORMAL", "999000000002", "20000001", "20000030");
        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> nos = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errs = new ConcurrentLinkedQueue<>();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        List<Thread> ts = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    String no = tx.execute(s -> stockService.takeNumber(e.getId(), "NORMAL").getInvoiceNo());
                    nos.add(no);
                } catch (Throwable ex) {
                    errs.add(ex);
                }
            });
            ts.add(t);
            t.start();
        }
        start.countDown();
        for (Thread t : ts) {
            t.join();
        }
        assertTrue(errs.isEmpty(), errs.isEmpty() ? "" : String.valueOf(errs.peek()));
        assertEquals(threads, nos.size());
        assertEquals(threads, new HashSet<>(nos).size());
    }

    private Invoice approvedInvoice(String suffix, String type, String amount, String rate) {
        TaxEntity e = fx.newEntity("T-I" + suffix, "91310000TI" + suffix + "001");
        fx.stock(e.getId(), type, "9990000000" + suffix, "30000001", "30000100");
        InvoiceRequest r = TestFixtures.req(e.getId(), type);
        InvoiceRequest saved = requestService.create(r, TestFixtures.one(amount, rate));
        requestService.transit(saved.getId(), "submit", null);
        requestService.transit(saved.getId(), "approve", null);
        return invoiceService.issue(saved.getId());
    }

    @Test
    void cancelRules() {
        // 电子票不可作废
        Invoice eInv = approvedInvoice("E1", "E_NORMAL", "100.00", "0.13");
        BizException ex = assertThrows(BizException.class, () -> invoiceService.cancel(eInv.getId(), "test"));
        assertTrue(ex.getMessage().contains("红冲"));

        // 纸质票当月可作废
        Invoice paper = approvedInvoice("P1", "NORMAL", "200.00", "0.13");
        Invoice cancelled = invoiceService.cancel(paper.getId(), "开错");
        assertEquals("CANCELLED", cancelled.getStatus());

        // 跨月不可作废：改库把开具日期改到上月
        Invoice paper2 = approvedInvoice("P2", "NORMAL", "300.00", "0.13");
        paper2.setIssueDate(java.time.LocalDate.now().minusMonths(1));
        invoiceMapperUpdate(paper2);
        ex = assertThrows(BizException.class, () -> invoiceService.cancel(paper2.getId(), "跨月"));
        assertTrue(ex.getMessage().contains("跨月"));
    }

    @Autowired
    com.inv.sales.mapper.InvoiceMapper invoiceMapper;

    private void invoiceMapperUpdate(Invoice inv) {
        invoiceMapper.updateById(inv);
    }

    @Test
    void redFlushPartialAndFull() {
        Invoice inv = approvedInvoice("R1", "E_SPECIAL", "1000.00", "0.13");
        // 部分红冲 400
        RedInfoLine rl = new RedInfoLine();
        rl.setGoodsName("测试商品");
        rl.setAmount(new BigDecimal("400.00"));
        rl.setTaxRate(new BigDecimal("0.13"));
        rl.setTaxAmount(new BigDecimal("52.00"));
        RedInfo info = invoiceService.createRedInfo(inv.getId(), "RETURN", Collections.singletonList(rl));
        assertEquals("DRAFT", info.getStatus());
        info = invoiceService.confirmRedInfo(info.getId());
        assertEquals("CONFIRMED", info.getStatus());
        assertNotNull(info.getRedInfoNo());
        Invoice red = invoiceService.redFlush(info.getId());
        assertEquals("RED", red.getStatus());
        assertEquals(new BigDecimal("-400.00"), red.getTotalAmount());

        Invoice after = invoiceService.requireInvoice(inv.getId());
        assertEquals("ISSUED", after.getStatus()); // 部分红冲仍是 ISSUED
        assertEquals(new BigDecimal("400.00"), after.getRedAmount());

        // 剩余部分全额红冲（不传行 = 按剩余）
        RedInfoLine rl2 = new RedInfoLine();
        rl2.setGoodsName("测试商品");
        rl2.setAmount(new BigDecimal("600.00"));
        rl2.setTaxRate(new BigDecimal("0.13"));
        rl2.setTaxAmount(new BigDecimal("78.00"));
        RedInfo info2 = invoiceService.createRedInfo(inv.getId(), "RETURN", Collections.singletonList(rl2));
        info2 = invoiceService.confirmRedInfo(info2.getId());
        invoiceService.redFlush(info2.getId());
        Invoice flushed = invoiceService.requireInvoice(inv.getId());
        assertEquals("RED_FLUSHED", flushed.getStatus());
        // 超额红冲被拒
        Invoice finalState = flushed;
        RedInfoLine over = new RedInfoLine();
        over.setGoodsName("x");
        over.setAmount(new BigDecimal("1.00"));
        over.setTaxRate(new BigDecimal("0.13"));
        assertThrows(BizException.class,
                () -> invoiceService.createRedInfo(finalState.getId(), "RETURN", Collections.singletonList(over)));
    }

    @Test
    void redFlushOverConcurrentInfos() {
        Invoice inv = approvedInvoice("R2", "E_SPECIAL", "1000.00", "0.13");
        // 两张各 60% 的 DRAFT 红字信息表均可创建确认
        RedInfoLine rl = new RedInfoLine();
        rl.setGoodsName("测试商品");
        rl.setAmount(new BigDecimal("600.00"));
        rl.setTaxRate(new BigDecimal("0.13"));
        rl.setTaxAmount(new BigDecimal("78.00"));
        RedInfo i1 = invoiceService.createRedInfo(inv.getId(), "RETURN", Collections.singletonList(rl));
        RedInfo i2 = invoiceService.createRedInfo(inv.getId(), "RETURN", Collections.singletonList(rl));
        invoiceService.confirmRedInfo(i1.getId());
        invoiceService.confirmRedInfo(i2.getId());
        invoiceService.redFlush(i1.getId());
        // 第二张红冲时剩余仅 40%，应被拒
        BizException ex = assertThrows(BizException.class, () -> invoiceService.redFlush(i2.getId()));
        assertTrue(ex.getMessage().contains("剩余可红"));
    }

    @Test
    void requestStateMachine() {
        TaxEntity e = fx.newEntity("T-SM", "91310000TESTSM001");
        InvoiceRequest r = requestService.create(TestFixtures.req(e.getId(), "E_NORMAL"), TestFixtures.one("10.00", "0.13"));
        // DRAFT 不能直接 approve
        assertThrows(BizException.class, () -> requestService.transit(r.getId(), "approve", null));
        requestService.transit(r.getId(), "submit", null);
        InvoiceRequest rej = requestService.transit(r.getId(), "reject", "信息不全");
        assertEquals("REJECTED", rej.getStatus());
        // REJECTED 可编辑回 DRAFT
        InvoiceRequest edited = requestService.update(r.getId(), TestFixtures.req(e.getId(), "E_NORMAL"), TestFixtures.one("20.00", "0.13"));
        assertEquals("DRAFT", edited.getStatus());
        requestService.transit(r.getId(), "submit", null);
        InvoiceRequest cancelled = requestService.transit(r.getId(), "cancel", null);
        assertEquals("CANCELLED", cancelled.getStatus());
    }
}
