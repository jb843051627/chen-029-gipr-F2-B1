package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TGiprArchiveRowMapper;
import com.fc.v2.model.auto.TGiprArchiveRow;

/**
 * 整批转档单测：空批/超上限不入库、批次幂等、合法/非法各落一次（旧实现非法行重复落两条）、
 * 返回成功行数。
 */
@ExtendWith(MockitoExtension.class)
class TGiprArchiveRowServiceImplTest {

    @Mock
    private TGiprArchiveRowMapper mapper;
    @InjectMocks
    private TGiprArchiveRowServiceImpl service;

    private TGiprArchiveRow row(String code, String qty) {
        TGiprArchiveRow r = new TGiprArchiveRow();
        r.setItemCode(code);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        return r;
    }

    @Test
    void 空批次返回0不碰库() {
        assertEquals(0, service.submitBatch("B1", new ArrayList<TGiprArchiveRow>()));
        assertEquals(0, service.submitBatch("B1", null));
        verify(mapper, never()).insert(any());
    }

    @Test
    void 缺批次码整批退回() {
        List<TGiprArchiveRow> rows = new ArrayList<>();
        rows.add(row("C1", "1"));
        assertEquals(-1, service.submitBatch("   ", rows));
        verify(mapper, never()).insert(any());
    }

    @Test
    void 超上限整批不入库返回负一() {
        List<TGiprArchiveRow> rows = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            rows.add(row("C" + i, "1"));
        }
        assertEquals(-1, service.submitBatch("BIG", rows));
        verify(mapper, never()).insert(any());
    }

    @Test
    void 合法非法逐行落库且非法行只落一次() {
        when(mapper.selectCount(any())).thenReturn(0);
        when(mapper.insert(any())).thenReturn(1);
        List<TGiprArchiveRow> rows = new ArrayList<>();
        rows.add(row("C1", "10"));   // 合法
        rows.add(row(null, "10"));  // 缺代号 → 已退回
        rows.add(row("C3", "0"));   // 卷数 0 → 已退回
        rows.add(row("C4", null));  // 缺卷数 → 已退回

        int ok = service.submitBatch("B2", rows);
        assertEquals(1, ok);
        // 四行各落一次，不再像旧实现那样先插失败行、随后又全量再插导致非法行落两条
        ArgumentCaptor<TGiprArchiveRow> cap = ArgumentCaptor.forClass(TGiprArchiveRow.class);
        verify(mapper, times(4)).insert(cap.capture());
        int[] got = cap.getAllValues().stream().mapToInt(TGiprArchiveRow::getStatus).toArray();
        assertEquals(1, got[0]);
        assertEquals(2, got[1]);
        assertEquals(2, got[2]);
        assertEquals(2, got[3]);
    }

    @Test
    void 同一批次重复提交不重复入库() {
        when(mapper.selectCount(any())).thenReturn(3);
        List<TGiprArchiveRow> rows = new ArrayList<>();
        rows.add(row("C1", "1"));
        rows.add(row("C2", "2"));
        // 已交过（库里有成功行）→ 不插入，直接回此前成功行数 3
        assertEquals(3, service.submitBatch("B3", rows));
        verify(mapper, never()).insert(any());
    }
}
