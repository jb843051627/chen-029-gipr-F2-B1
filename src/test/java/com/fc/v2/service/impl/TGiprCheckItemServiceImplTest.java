package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TGiprCheckItemMapper;
import com.fc.v2.model.auto.TGiprCheckItem;

/**
 * 年度提醒单测：到期压界可处理、至多提前窗口、只取搁着条目、空集合不空指针、
 * runOnce 逐条发单条失败跳过。
 */
@ExtendWith(MockitoExtension.class)
class TGiprCheckItemServiceImplTest {

    @Mock
    private TGiprCheckItemMapper mapper;
    @InjectMocks
    private TGiprCheckItemServiceImpl service;

    private static final long DAY = 24L * 3600 * 1000;
    private static final Date DUE = new Date(1_700_000_000_000L);

    private TGiprCheckItem item(Long id, String advanceDays) {
        TGiprCheckItem r = new TGiprCheckItem();
        r.setId(id);
        r.setDueAt(DUE);
        r.setStatus(0);
        r.setDelFlag(0);
        if (advanceDays != null) {
            r.setAmount(new BigDecimal(advanceDays));
        }
        return r;
    }

    private void mockRows(TGiprCheckItem... rows) {
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(Arrays.asList(rows)));
    }

    @Test
    void 到期当日压界即可处理() {
        mockRows(item(1L, null)); // amount 空按 0 天
        assertEquals(1, service.listDue(DUE).size());
        // 差一天未到期
        assertEquals(0, service.listDue(new Date(DUE.getTime() - 1)).size());
    }

    @Test
    void 提前窗口压界可处理() {
        mockRows(item(1L, "2"));
        Date earliest = new Date(DUE.getTime() - 2 * DAY);
        assertEquals(1, service.listDue(earliest).size(), "恰好到最早可处理时刻应可处理（旧实现 < 严格小于会漏掉）");
        assertEquals(0, service.listDue(new Date(DUE.getTime() - 2 * DAY - 1)).size());
        assertEquals(1, service.listDue(new Date(DUE.getTime() - DAY)).size());
    }

    @Test
    void 无到期条目runOnce返回0不空指针() {
        when(mapper.selectList(any())).thenReturn(Collections.<TGiprCheckItem>emptyList());
        assertEquals(0, service.runOnce(DUE));
        verify(mapper, never()).updateById(any());
    }

    @Test
    void at为空返回空集() {
        assertEquals(0, service.listDue(null).size());
        assertEquals(0, service.runOnce(null));
    }

    @Test
    void runOnce逐条发出落已发出() {
        mockRows(item(1L, null), item(2L, null));
        when(mapper.updateById(any())).thenReturn(1);
        int ok = service.runOnce(DUE);
        assertEquals(2, ok);
        ArgumentCaptor<TGiprCheckItem> cap = ArgumentCaptor.forClass(TGiprCheckItem.class);
        verify(mapper, times(2)).updateById(cap.capture());
        for (TGiprCheckItem r : cap.getAllValues()) {
            assertEquals(1, (int) r.getStatus());
        }
    }

    @Test
    void 单条发不成跳过其余继续() {
        TGiprCheckItem bad = item(1L, null);
        TGiprCheckItem good = item(2L, null);
        List<TGiprCheckItem> rows = new ArrayList<>(Arrays.asList(bad, good));
        when(mapper.selectList(any())).thenReturn(rows);
        // 第一条更新两次都抛（发出失败 + 落发不成也抛），第二条正常
        when(mapper.updateById(bad)).thenThrow(new RuntimeException("db down"));
        when(mapper.updateById(good)).thenReturn(1);

        int ok = service.runOnce(DUE);
        assertEquals(1, ok, "坏条跳过，好条照发");
    }
}
