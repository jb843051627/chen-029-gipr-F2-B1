package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TGiprCertBillMapper;
import com.fc.v2.model.auto.TGiprCertBill;

/**
 * 签批单测：一名即齐/两名同判、签到齐才过关、省局签齐点齐、退回一步、终态冻结。
 */
@ExtendWith(MockitoExtension.class)
class TGiprCertBillServiceImplTest {

    @Mock
    private TGiprCertBillMapper mapper;
    @InjectMocks
    private TGiprCertBillServiceImpl service;

    private TGiprCertBill bill(int node, int mode, Integer need, int signed, int status) {
        TGiprCertBill b = new TGiprCertBill();
        b.setId(1L);
        b.setNodeNo(node);
        b.setSignMode(mode);
        b.setNeedCount(need);
        b.setSignCount(signed);
        b.setStatus(status);
        b.setDelFlag(0);
        return b;
    }

    @Test
    void 一名即齐一票过县关进市关() {
        when(mapper.selectById(1L)).thenReturn(bill(0, 0, null, 0, 0));
        TGiprCertBill b = service.approve(1L, "张", "同意");
        assertEquals(1, b.getNodeNo());
        assertEquals(0, b.getSignCount());
        assertEquals(0, b.getStatus());
    }

    @Test
    void 两名同判首票留在本关次票才过关() {
        when(mapper.selectById(1L)).thenReturn(bill(0, 1, 2, 0, 0));
        TGiprCertBill first = service.approve(1L, "张", "同意");
        assertEquals(0, first.getNodeNo());
        assertEquals(1, first.getSignCount());

        when(mapper.selectById(1L)).thenReturn(bill(0, 1, 2, 1, 0));
        TGiprCertBill second = service.approve(1L, "李", "同意");
        assertEquals(1, second.getNodeNo());
        assertEquals(0, second.getSignCount());
    }

    @Test
    void 省局签齐即点齐() {
        // 第 2 关一名即齐
        when(mapper.selectById(1L)).thenReturn(bill(2, 0, null, 0, 0));
        TGiprCertBill b = service.approve(1L, "省", "核准");
        assertEquals(2, b.getNodeNo());
        assertEquals(1, b.getStatus());
    }

    @Test
    void 退回上一环节为一步() {
        when(mapper.selectById(1L)).thenReturn(bill(2, 0, null, 0, 0));
        TGiprCertBill b = service.rollback(1L, "补");
        assertEquals(1, b.getNodeNo());
        assertEquals(0, b.getSignCount());
    }

    @Test
    void 否决压回且终态冻结() {
        when(mapper.selectById(1L)).thenReturn(bill(1, 0, null, 0, 0));
        TGiprCertBill b = service.reject(1L, "王", "不合规");
        assertEquals(2, b.getStatus());

        when(mapper.selectById(1L)).thenReturn(bill(1, 0, null, 0, 2));
        assertNull(service.approve(1L, "王", "再签"), "已压回不得再签");
        assertNull(service.rollback(1L, "退"), "已压回不得退回");
        verify(mapper, never()).deleteById(any());
    }

    @Test
    void 未署审批人拒签() {
        when(mapper.selectById(1L)).thenReturn(bill(0, 0, null, 0, 0));
        assertNull(service.approve(1L, "  ", "x"));
    }
}
