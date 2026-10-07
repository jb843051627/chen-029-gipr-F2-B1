package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TGiprArchiveRowMapper;
import com.fc.v2.mapper.auto.TGiprCaseFlowMapper;
import com.fc.v2.mapper.auto.TGiprCertBillMapper;
import com.fc.v2.mapper.auto.TGiprCheckItemMapper;
import com.fc.v2.mapper.auto.TGiprProductBookMapper;
import com.fc.v2.mapper.auto.TGiprTermBookMapper;
import com.fc.v2.mapper.auto.TGiprYieldRuleMapper;
import com.fc.v2.model.auto.TGiprArchiveRow;
import com.fc.v2.model.auto.TGiprCaseFlow;
import com.fc.v2.model.auto.TGiprCertBill;
import com.fc.v2.model.auto.TGiprCheckItem;
import com.fc.v2.model.auto.TGiprProductBook;
import com.fc.v2.model.auto.TGiprTermBook;
import com.fc.v2.model.auto.TGiprYieldRule;

/**
 * 判档/名录/档册/流转/签批/提醒六处口径修复的回归测试（mapper 全部打桩，纯内存验证）
 */
class GiprServiceFixTest {

    private static final Date NOW = new Date();

    private TGiprYieldRuleMapper yieldMapper;
    private TGiprYieldRuleServiceImpl yieldService;
    private TGiprArchiveRowMapper archiveMapper;
    private TGiprArchiveRowServiceImpl archiveService;
    private TGiprCaseFlowMapper flowMapper;
    private TGiprCaseFlowServiceImpl flowService;
    private TGiprCertBillMapper billMapper;
    private TGiprCertBillServiceImpl billService;
    private TGiprCheckItemMapper checkMapper;
    private TGiprCheckItemServiceImpl checkService;
    private TGiprTermBookMapper termMapper;
    private TGiprProductBookMapper prodMapper;
    private TGiprTermBookServiceImpl termService;

    @BeforeEach
    void setUp() {
        yieldMapper = mock(TGiprYieldRuleMapper.class);
        yieldService = new TGiprYieldRuleServiceImpl();
        ReflectionTestUtils.setField(yieldService, "giprYieldRuleMapper", yieldMapper);

        archiveMapper = mock(TGiprArchiveRowMapper.class);
        archiveService = new TGiprArchiveRowServiceImpl();
        ReflectionTestUtils.setField(archiveService, "giprArchiveRowMapper", archiveMapper);

        flowMapper = mock(TGiprCaseFlowMapper.class);
        flowService = new TGiprCaseFlowServiceImpl();
        ReflectionTestUtils.setField(flowService, "giprCaseFlowMapper", flowMapper);

        billMapper = mock(TGiprCertBillMapper.class);
        billService = new TGiprCertBillServiceImpl();
        ReflectionTestUtils.setField(billService, "giprCertBillMapper", billMapper);

        checkMapper = mock(TGiprCheckItemMapper.class);
        checkService = new TGiprCheckItemServiceImpl();
        ReflectionTestUtils.setField(checkService, "giprCheckItemMapper", checkMapper);

        termMapper = mock(TGiprTermBookMapper.class);
        prodMapper = mock(TGiprProductBookMapper.class);
        termService = new TGiprTermBookServiceImpl();
        ReflectionTestUtils.setField(termService, "baseMapper", termMapper);
        ReflectionTestUtils.setField(termService, "giprProductBookMapper", prodMapper);
    }

    // ================= 贴线档（判档） =================

    private static TGiprYieldRule rule(String code, int priority, String th1, String th2, String th3) {
        TGiprYieldRule r = new TGiprYieldRule();
        r.setRuleCode(code);
        r.setPriority(Integer.valueOf(priority));
        r.setTh1Max(new BigDecimal(th1));
        r.setTh2Max(new BigDecimal(th2));
        r.setTh3Max(new BigDecimal(th3));
        r.setStatus(Integer.valueOf(0));
        r.setDelFlag(Integer.valueOf(0));
        return r;
    }

    @Test
    void boundaryEqualToLimitTakesHigherBand() {
        TGiprYieldRule r = rule("R1", 1, "10", "20", "30");
        when(yieldMapper.selectList(any())).thenReturn(Arrays.asList(r));
        // 等于上限取高一档：压界归高一头
        assertEquals(2, yieldService.evaluate("R1", new BigDecimal("10"), NOW));
        assertEquals(3, yieldService.evaluate("R1", new BigDecimal("20"), NOW));
        assertEquals(4, yieldService.evaluate("R1", new BigDecimal("30"), NOW));
        // 未压界照旧
        assertEquals(1, yieldService.evaluate("R1", new BigDecimal("9.999"), NOW));
        assertEquals(2, yieldService.evaluate("R1", new BigDecimal("19.999"), NOW));
        assertEquals(4, yieldService.evaluate("R1", new BigDecimal("30.001"), NOW));
    }

    @Test
    void unknownRuleCodeReturnsZeroInsteadOfNpe() {
        when(yieldMapper.selectList(any())).thenReturn(new ArrayList<TGiprYieldRule>());
        assertEquals(0, yieldService.evaluate("NO_SUCH_CODE", new BigDecimal("5"), NOW));
    }

    @Test
    void outOfRangeInputRejectedOnBothEntries() {
        // 异常大的输入不得出档；越界时连库都不必查
        assertEquals(0, yieldService.evaluate("R1", new BigDecimal("101"), NOW));
        assertEquals(0, yieldService.evaluate("R1", new BigDecimal("-1"), NOW));
        assertEquals(0, yieldService.evaluateTop(new BigDecimal("100000"), NOW));
        verifyNoInteractions(yieldMapper);
    }

    @Test
    void evaluateTopPicksHighestPriorityNotDbOrder() {
        TGiprYieldRule low = rule("LOW", 1, "5", "50", "80");
        TGiprYieldRule high = rule("HIGH", 9, "50", "80", "90");
        // 库返回排列：低优先级在前；取哪条必须看优先级
        when(yieldMapper.selectList(any())).thenReturn(Arrays.asList(low, high));
        // 输入 10：HIGH 线 → 1 档；LOW 线 → 2 档
        assertEquals(1, yieldService.evaluateTop(new BigDecimal("10"), NOW));
    }

    @Test
    void samePriorityTieBreakIsDeterministic() {
        TGiprYieldRule a = rule("AAA", 5, "5", "50", "80");
        TGiprYieldRule b = rule("BBB", 5, "50", "80", "90");
        when(yieldMapper.selectList(any())).thenReturn(Arrays.asList(a, b));
        // 同优先级按 ruleCode 降序取 BBB，两次结果必须一致
        assertEquals(1, yieldService.evaluateTop(new BigDecimal("10"), NOW));
        assertEquals(1, yieldService.evaluateTop(new BigDecimal("10"), NOW));
    }

    @Test
    void listAvailableFiltersAndOrders() {
        TGiprYieldRule low = rule("LOW", 1, "5", "50", "80");
        TGiprYieldRule high = rule("HIGH", 9, "50", "80", "90");
        TGiprYieldRule mid = rule("MID", 5, "50", "80", "90");
        when(yieldMapper.selectList(any())).thenReturn(Arrays.asList(low, high, mid));

        List<TGiprYieldRule> avail = yieldService.listAvailable(NOW);
        assertEquals(Arrays.asList(high, mid, low), avail);

        // 可用判据必须落到 SQL：未删除 + 在位 + 生效区间（停用项不得混入）
        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(yieldMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("del_flag"), sql);
        assertTrue(sql.contains("status"), sql);
        assertTrue(sql.contains("eff_start"), sql);
        assertTrue(sql.contains("eff_end"), sql);
    }

    @Test
    void usableSharesTheSamePredicate() {
        TGiprYieldRule ok = rule("R1", 1, "10", "20", "30");
        when(yieldMapper.selectById(1L)).thenReturn(ok);
        assertTrue(yieldService.usable(1L, NOW));

        TGiprYieldRule disabled = rule("R2", 1, "10", "20", "30");
        disabled.setStatus(Integer.valueOf(1));
        when(yieldMapper.selectById(2L)).thenReturn(disabled);
        assertFalse(yieldService.usable(2L, NOW));

        TGiprYieldRule notYet = rule("R3", 1, "10", "20", "30");
        notYet.setEffStart(new Date(NOW.getTime() + 86400000L));
        when(yieldMapper.selectById(3L)).thenReturn(notYet);
        assertFalse(yieldService.usable(3L, NOW));

        TGiprYieldRule handedOver = rule("R4", 1, "10", "20", "30");
        handedOver.setEffEnd(new Date(NOW.getTime() - 86400000L));
        when(yieldMapper.selectById(4L)).thenReturn(handedOver);
        assertFalse(yieldService.usable(4L, NOW));

        when(yieldMapper.selectById(5L)).thenReturn(null);
        assertFalse(yieldService.usable(5L, NOW));
    }

    @Test
    void countAvailableConsistentWithList() {
        when(yieldMapper.selectList(any())).thenReturn(Arrays.asList(rule("A", 1, "1", "2", "3"), rule("B", 2, "1", "2", "3")));
        assertEquals(2, yieldService.countAvailable(NOW));
        assertEquals(0, yieldService.countAvailable(null));
    }

    // ================= 档册（整批提交） =================

    private static TGiprArchiveRow row(String itemCode, String qty) {
        TGiprArchiveRow r = new TGiprArchiveRow();
        r.setItemCode(itemCode);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        return r;
    }

    @Test
    void emptyBatchReturnsZero() {
        assertEquals(0, archiveService.submitBatch("B1", new ArrayList<TGiprArchiveRow>()));
        assertEquals(0, archiveService.submitBatch("B1", null));
        verifyNoInteractions(archiveMapper);
    }

    @Test
    void oversizeBatchRejectedWholesale() {
        List<TGiprArchiveRow> rows = new ArrayList<TGiprArchiveRow>();
        for (int i = 0; i < 501; i++) {
            rows.add(row("IT" + i, "1"));
        }
        assertEquals(-1, archiveService.submitBatch("B1", rows));
        verify(archiveMapper, never()).insert(any());
    }

    @Test
    void mixedBatchFilesValidAndRecordsFailures() {
        when(archiveMapper.selectList(any())).thenReturn(new ArrayList<TGiprArchiveRow>());
        when(archiveMapper.insert(any())).thenReturn(1);
        List<TGiprArchiveRow> rows = Arrays.asList(
                row("IT1", "10"), row("", "10"), row("IT3", "0"), row("IT4", "5"));
        assertEquals(2, archiveService.submitBatch("B1", rows));

        ArgumentCaptor<TGiprArchiveRow> captor = ArgumentCaptor.forClass(TGiprArchiveRow.class);
        verify(archiveMapper, times(4)).insert(captor.capture());
        List<TGiprArchiveRow> inserted = captor.getAllValues();
        // 合法非法都落库、保留原始行号、批次号以入参为准
        for (int i = 0; i < 4; i++) {
            assertEquals(Integer.valueOf(i + 1), inserted.get(i).getRowNo());
            assertEquals("B1", inserted.get(i).getBatchNo());
        }
        assertEquals(Integer.valueOf(1), inserted.get(0).getStatus());
        assertEquals(Integer.valueOf(2), inserted.get(1).getStatus());
        assertEquals(Integer.valueOf(2), inserted.get(2).getStatus());
        assertEquals(Integer.valueOf(1), inserted.get(3).getStatus());
    }

    @Test
    void resubmitSameBatchIsIdempotent() {
        when(archiveMapper.insert(any())).thenReturn(1);
        when(archiveMapper.selectList(any())).thenReturn(new ArrayList<TGiprArchiveRow>());
        List<TGiprArchiveRow> rows = Arrays.asList(row("IT1", "10"), row("IT2", "5"));
        assertEquals(2, archiveService.submitBatch("B1", rows));

        // 同一批次再来一遍：不得重复入库，只认头一遭算出来的结果
        TGiprArchiveRow done1 = row("IT1", "10");
        done1.setStatus(Integer.valueOf(1));
        TGiprArchiveRow done2 = row("IT2", "5");
        done2.setStatus(Integer.valueOf(1));
        when(archiveMapper.selectList(any())).thenReturn(Arrays.asList(done1, done2));
        assertEquals(2, archiveService.submitBatch("B1", rows));
        verify(archiveMapper, times(2)).insert(any());
    }

    @Test
    void listErrorsOrderedByRowNo() {
        when(archiveMapper.selectList(any())).thenReturn(new ArrayList<TGiprArchiveRow>());
        archiveService.listErrors("B1");
        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(archiveMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("ORDER BY"), sql);
        assertTrue(sql.contains("row_no"), sql);
    }

    // ================= 流转单（state-machine） =================

    private static TGiprCaseFlow flow(int stage, int status) {
        TGiprCaseFlow r = new TGiprCaseFlow();
        r.setStage(Integer.valueOf(stage));
        r.setStatus(Integer.valueOf(status));
        return r;
    }

    @Test
    void advanceMovesOneStageAndSealsAtEnd() {
        TGiprCaseFlow r = flow(0, 1);
        when(flowMapper.selectById(1L)).thenReturn(r);
        when(flowMapper.updateById(any())).thenReturn(1);
        flowService.advance(1L, "go");
        assertEquals(Integer.valueOf(1), r.getStage());
        assertEquals(Integer.valueOf(1), r.getStatus());

        r.setStage(Integer.valueOf(2));
        flowService.advance(1L, "go");
        assertEquals(Integer.valueOf(3), r.getStage());
        assertEquals(Integer.valueOf(2), r.getStatus());

        // 已封住不得再推进
        assertNull(flowService.advance(1L, "go"));
        assertEquals(Integer.valueOf(3), r.getStage());
    }

    @Test
    void rollbackMovesOneStageBack() {
        TGiprCaseFlow r = flow(3, 2);
        when(flowMapper.selectById(1L)).thenReturn(r);
        when(flowMapper.updateById(any())).thenReturn(1);
        flowService.rollback(1L, "back");
        // 回退一档而非打回起点；退回后重新在办
        assertEquals(Integer.valueOf(2), r.getStage());
        assertEquals(Integer.valueOf(1), r.getStatus());

        flowService.rollback(1L, "back");
        flowService.rollback(1L, "back");
        assertEquals(Integer.valueOf(0), r.getStage());
        flowService.rollback(1L, "back");
        assertEquals(Integer.valueOf(0), r.getStage());
    }

    @Test
    void terminalFlowCannotBeEditedOrRemoved() {
        TGiprCaseFlow r = flow(3, 2);
        when(flowMapper.selectById(1L)).thenReturn(r);
        assertFalse(flowService.updateContent(1L, "x"));
        assertFalse(flowService.remove(1L));
    }

    // ================= 签批单（approval-chain） =================

    private static TGiprCertBill bill(int node, int mode, int need, int signed, int status) {
        TGiprCertBill r = new TGiprCertBill();
        r.setNodeNo(Integer.valueOf(node));
        r.setSignMode(Integer.valueOf(mode));
        r.setNeedCount(Integer.valueOf(need));
        r.setSignCount(Integer.valueOf(signed));
        r.setStatus(Integer.valueOf(status));
        return r;
    }

    @Test
    void orModeOneSignatureCompletesNode() {
        TGiprCertBill r = bill(0, 0, 1, 0, 0);
        when(billMapper.selectById(1L)).thenReturn(r);
        when(billMapper.updateById(any())).thenReturn(1);
        billService.approve(1L, "zhangsan", "ok");
        assertEquals(Integer.valueOf(1), r.getNodeNo());
        assertEquals(Integer.valueOf(0), r.getSignCount());
        assertEquals(Integer.valueOf(0), r.getStatus());

        r.setNodeNo(Integer.valueOf(2));
        billService.approve(1L, "lisi", "ok");
        assertEquals(Integer.valueOf(1), r.getStatus());
    }

    @Test
    void andModeNeedsAllSignatures() {
        TGiprCertBill r = bill(0, 1, 2, 0, 0);
        when(billMapper.selectById(1L)).thenReturn(r);
        when(billMapper.updateById(any())).thenReturn(1);
        billService.approve(1L, "zhangsan", "ok");
        // 两名须同判：第一票不够，环节不动
        assertEquals(Integer.valueOf(0), r.getNodeNo());
        assertEquals(Integer.valueOf(1), r.getSignCount());
        assertEquals(Integer.valueOf(0), r.getStatus());

        billService.approve(1L, "lisi", "ok");
        assertEquals(Integer.valueOf(1), r.getNodeNo());
        assertEquals(Integer.valueOf(0), r.getSignCount());
    }

    @Test
    void rejectSetsVetoAndTerminalRefusesFurtherVotes() {
        TGiprCertBill r = bill(1, 0, 1, 0, 0);
        when(billMapper.selectById(1L)).thenReturn(r);
        when(billMapper.updateById(any())).thenReturn(1);
        billService.reject(1L, "zhangsan", "no");
        assertEquals(Integer.valueOf(2), r.getStatus());
        assertNull(billService.approve(1L, "lisi", "ok"));
    }

    @Test
    void billRollbackStepsBackOneNodeAndReopens() {
        TGiprCertBill r = bill(2, 0, 1, 0, 1);
        when(billMapper.selectById(1L)).thenReturn(r);
        when(billMapper.updateById(any())).thenReturn(1);
        billService.rollback(1L, "rework");
        assertEquals(Integer.valueOf(1), r.getNodeNo());
        assertEquals(Integer.valueOf(0), r.getSignCount());
        assertEquals(Integer.valueOf(0), r.getStatus());
    }

    // ================= 提醒条目（scheduling-job） =================

    private static TGiprCheckItem item(long dueInDays, BigDecimal amount) {
        TGiprCheckItem r = new TGiprCheckItem();
        r.setDueAt(new Date(NOW.getTime() + dueInDays * 86400000L));
        r.setAmount(amount);
        r.setStatus(Integer.valueOf(0));
        return r;
    }

    @Test
    void listDueRespectsAdvanceWindowAndDefaults() {
        TGiprCheckItem inWindow = item(3, new BigDecimal("5"));
        TGiprCheckItem tooEarly = item(10, new BigDecimal("5"));
        TGiprCheckItem pastDue = item(-1, new BigDecimal("5"));
        TGiprCheckItem outOfRangeAmount = item(3, new BigDecimal("99"));
        TGiprCheckItem nullAmountTooEarly = item(7, null);
        when(checkMapper.selectList(any())).thenReturn(
                Arrays.asList(inWindow, tooEarly, pastDue, outOfRangeAmount, nullAmountTooEarly));

        List<TGiprCheckItem> due = checkService.listDue(NOW);
        assertEquals(2, due.size());
        assertTrue(due.contains(inWindow));
        assertTrue(due.contains(outOfRangeAmount));
    }

    @Test
    void runOnceMarksDoneAndCounts() {
        TGiprCheckItem a = item(1, new BigDecimal("5"));
        TGiprCheckItem b = item(2, new BigDecimal("5"));
        when(checkMapper.selectList(any())).thenReturn(Arrays.asList(a, b));
        when(checkMapper.updateById(any())).thenReturn(1);
        assertEquals(2, checkService.runOnce(NOW));
        assertEquals(Integer.valueOf(1), a.getStatus());
        assertEquals(Integer.valueOf(1), b.getStatus());
    }

    @Test
    void runOnceOnEmptyDoesNotCrash() {
        when(checkMapper.selectList(any())).thenReturn(new ArrayList<TGiprCheckItem>());
        assertEquals(0, checkService.runOnce(NOW));
    }

    @Test
    void runOnceSkipsSingleFailureAndContinues() {
        TGiprCheckItem bad = item(1, new BigDecimal("5"));
        TGiprCheckItem good = item(2, new BigDecimal("5"));
        when(checkMapper.selectList(any())).thenReturn(Arrays.asList(bad, good));
        when(checkMapper.updateById(any()))
                .thenThrow(new RuntimeException("db down"))
                .thenReturn(0)
                .thenReturn(1);
        assertEquals(1, checkService.runOnce(NOW));
        assertEquals(Integer.valueOf(2), bad.getStatus());
        assertEquals(Integer.valueOf(1), good.getStatus());
    }

    // ================= 名录词条册 =================

    @Test
    void insertComputesLackAsCrtMinusHit() {
        TGiprProductBook prod = new TGiprProductBook();
        prod.setStatus(Integer.valueOf(0));
        when(prodMapper.selectOne(any())).thenReturn(prod);
        when(termMapper.selectCount(any())).thenReturn(Integer.valueOf(0));
        when(termMapper.insert(any())).thenReturn(1);

        TGiprTermBook record = new TGiprTermBook();
        record.setProdId(Integer.valueOf(0));
        record.setCrtNum(Integer.valueOf(10));
        record.setHitNum(Integer.valueOf(3));
        assertEquals(1, termService.insertTGiprTermBook(record));

        ArgumentCaptor<TGiprTermBook> captor = ArgumentCaptor.forClass(TGiprTermBook.class);
        verify(termMapper).insert(captor.capture());
        // 还差几条 = 应收录 - 已对齐
        assertEquals(Integer.valueOf(7), captor.getValue().getLackNum());
    }

    @Test
    void updateRecomputesLackWithSameFormula() {
        TGiprTermBook current = new TGiprTermBook();
        current.setCrtNum(Integer.valueOf(10));
        current.setHitNum(Integer.valueOf(3));
        when(termMapper.selectById(1L)).thenReturn(current);
        when(termMapper.update(any(), any())).thenReturn(1);

        TGiprTermBook record = new TGiprTermBook();
        record.setId(1L);
        record.setHitNum(Integer.valueOf(6));
        assertEquals(1, termService.updateTGiprTermBook(record));

        ArgumentCaptor<TGiprTermBook> captor = ArgumentCaptor.forClass(TGiprTermBook.class);
        verify(termMapper).update(captor.capture(), any());
        assertEquals(Integer.valueOf(4), captor.getValue().getLackNum());
    }

    @Test
    void listUsesCallerWrapperAndCallerPagination() {
        QueryWrapper<TGiprTermBook> wrapper = new QueryWrapper<TGiprTermBook>().eq("del_flag", 0);
        when(termMapper.selectList(any())).thenReturn(new ArrayList<TGiprTermBook>());
        termService.selectTGiprTermBookList(wrapper);
        // 调用方条件原样下达，不在服务内另起炉灶
        verify(termMapper).selectList(wrapper);
        assertNotNull(wrapper);
    }
}
