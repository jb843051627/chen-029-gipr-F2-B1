package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TGiprYieldRuleMapper;
import com.fc.v2.model.auto.TGiprYieldRule;

/**
 * 判档统一口径单测：对应县局定线岗报来的"停用混入、压界、量程、未知编号空指针、
 * 规则选取看库排列、同优先级两次不同档"等问题。
 */
@ExtendWith(MockitoExtension.class)
class TGiprYieldRuleServiceImplTest {

    @Mock
    private TGiprYieldRuleMapper mapper;

    @InjectMocks
    private TGiprYieldRuleServiceImpl service;

    private static final Date T = new Date(1_700_000_000_000L);
    private static final long DAY = 24L * 3600 * 1000;

    /** 造一条在位、窗口覆盖 T 的线 */
    private TGiprYieldRule rule(long id, String code, Integer pri, Integer status,
                               Date start, Date end, String th1, String th2, String th3) {
        TGiprYieldRule r = new TGiprYieldRule();
        r.setId(id);
        r.setRuleCode(code);
        r.setPriority(pri);
        r.setStatus(status);
        r.setEffStart(start);
        r.setEffEnd(end);
        r.setDelFlag(0);
        if (th1 != null) r.setTh1Max(new BigDecimal(th1));
        if (th2 != null) r.setTh2Max(new BigDecimal(th2));
        if (th3 != null) r.setTh3Max(new BigDecimal(th3));
        return r;
    }

    private TGiprYieldRule activeRule(long id, String code, Integer pri) {
        return rule(id, code, pri, 0,
                new Date(T.getTime() - DAY), new Date(T.getTime() + DAY),
                "10", "20", "30");
    }

    @Test
    void 停用的线不混进可用集与判档() {
        TGiprYieldRule on = activeRule(1, "R_ON", 5);
        TGiprYieldRule off = rule(2, "R_OFF", 9, 1,
                new Date(T.getTime() - DAY), new Date(T.getTime() + DAY), "1", "2", "3");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(on, off));

        List<TGiprYieldRule> avail = service.listAvailable(T);
        assertEquals(1, avail.size());
        assertEquals("R_ON", avail.get(0).getRuleCode());
        assertEquals(1, service.countAvailable(T));
        // 优先级更高但已撤下的线不得参与判档
        assertEquals(1, service.evaluateTop(new BigDecimal("5"), T));
    }

    @Test
    void 生效窗口压界_交班日不含_启用日含() {
        TGiprYieldRule r = rule(1, "R", 1, 0, T, new Date(T.getTime() + DAY), "10", "20", "30");
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(r));
        // at == eff_start：在窗口内
        assertEquals(1, service.evaluateTop(new BigDecimal("5"), T));

        // at == eff_end：交班日不含，已失效，无可用线
        assertEquals(0, service.evaluateTop(new BigDecimal("5"), new Date(T.getTime() + DAY)));
        // at 早于 eff_start：未启用
        assertEquals(0, service.evaluateTop(new BigDecimal("5"), new Date(T.getTime() - 1)));
    }

    @Test
    void 等于上限归高一档() {
        TGiprYieldRule r = activeRule(1, "R", 1);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(r));
        assertEquals(1, service.evaluate("R", new BigDecimal("9.999"), T));
        // 恰好等于 th1：归 2 档（旧实现归 1 档）
        assertEquals(2, service.evaluate("R", new BigDecimal("10"), T));
        // 恰好等于 th2：归 3 档
        assertEquals(3, service.evaluate("R", new BigDecimal("20"), T));
        // 恰好等于封顶 th3：仍在量程内，归 3 档（旧实现把等于上限算低档/越界）
        assertEquals(3, service.evaluate("R", new BigDecimal("30"), T));
    }

    @Test
    void 异常大输入和负数超量程返回0而非硬算档位() {
        TGiprYieldRule r = activeRule(1, "R", 1);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(r));
        assertEquals(0, service.evaluate("R", new BigDecimal("30.001"), T));
        assertEquals(0, service.evaluate("R", new BigDecimal("999999999"), T));
        assertEquals(0, service.evaluate("R", new BigDecimal("-1"), T));
        // evaluate 与 evaluateTop 量程口径一致
        assertEquals(0, service.evaluateTop(new BigDecimal("30.001"), T));
    }

    @Test
    void 用没建过的编号求值返回0不空指针() {
        when(mapper.selectList(any())).thenReturn(Collections.<TGiprYieldRule>emptyList());
        assertEquals(0, service.evaluate("NOT_EXIST", new BigDecimal("5"), T));
    }

    @Test
    void 多条同时可用只认优先级最高且两次结果稳定() {
        // 库返回顺序故意把低优先级放前面，且与优先级相反
        TGiprYieldRule low = activeRule(10, "AAA", 1);   // 低优先级，区间窄
        TGiprYieldRule high = rule(20, "BBB", 9, 0,
                new Date(T.getTime() - DAY), new Date(T.getTime() + DAY),
                "100", "200", "300");                     // 高优先级，区间宽
        when(mapper.selectList(any())).thenReturn(Arrays.asList(low, high));

        BigDecimal input = new BigDecimal("150"); // 在 low 里超量程，在 high 里归 2 档
        int first = service.evaluateTop(input, T);
        int second = service.evaluateTop(input, T);
        assertEquals(2, first);
        assertEquals(2, second);
        assertEquals("BBB", service.listAvailable(T).get(0).getRuleCode());
    }

    @Test
    void 同优先级按代号再按id定先后不看库排列() {
        TGiprYieldRule a = activeRule(1, "CODE_A", 5);
        TGiprYieldRule b = activeRule(2, "CODE_B", 5);
        // 两种库返回顺序都必须选出 CODE_B
        when(mapper.selectList(any())).thenReturn(Arrays.asList(a, b));
        assertEquals("CODE_B", service.listAvailable(T).get(0).getRuleCode());
        when(mapper.selectList(any())).thenReturn(Arrays.asList(b, a));
        assertEquals("CODE_B", service.listAvailable(T).get(0).getRuleCode());
    }

    @Test
    void usable与列表同一口径() {
        TGiprYieldRule on = activeRule(1, "R_ON", 5);
        TGiprYieldRule off = rule(2, "R_OFF", 5, 1,
                new Date(T.getTime() - DAY), new Date(T.getTime() + DAY), "10", "20", "30");
        when(mapper.selectById(1L)).thenReturn(on);
        when(mapper.selectById(2L)).thenReturn(off);

        assertTrue(service.usable(1L, T));
        // 已撤下
        assertFalse(service.usable(2L, T));
        // 窗口外
        assertFalse(service.usable(1L, new Date(T.getTime() + 2 * DAY)));
        // 不存在
        assertFalse(service.usable(999L, T));
    }

    @Test
    void 参数缺失返回0() {
        assertEquals(0, service.evaluate(null, new BigDecimal("5"), T));
        assertEquals(0, service.evaluate("R", null, T));
        assertEquals(0, service.evaluate("R", new BigDecimal("5"), null));
        assertEquals(0, service.evaluateTop(null, T));
        assertEquals(0, service.evaluateTop(new BigDecimal("5"), null));
        assertEquals(0, service.countAvailable(null));
    }
}
