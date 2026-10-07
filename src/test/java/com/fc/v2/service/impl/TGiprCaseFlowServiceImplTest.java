package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TGiprCaseFlowMapper;
import com.fc.v2.model.auto.TGiprCaseFlow;

/**
 * 办件流转单测：推进/回退各一步、到末关封住、已封住冻结、软删；
 * 对应"归档与回退两处口径不统一"的核对。
 */
@ExtendWith(MockitoExtension.class)
class TGiprCaseFlowServiceImplTest {

    @Mock
    private TGiprCaseFlowMapper mapper;
    @InjectMocks
    private TGiprCaseFlowServiceImpl service;

    private TGiprCaseFlow flow(int stage, int status) {
        TGiprCaseFlow r = new TGiprCaseFlow();
        r.setId(1L);
        r.setStage(stage);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    @Test
    void 推进一次只过一关() {
        when(mapper.selectById(1L)).thenReturn(flow(0, 0));
        TGiprCaseFlow r = service.advance(1L, "受理");
        assertEquals(1, r.getStage());
        assertEquals(1, r.getStatus());
        verify(mapper).updateById(any());
    }

    @Test
    void 推到末关即封住() {
        when(mapper.selectById(1L)).thenReturn(flow(2, 1));
        TGiprCaseFlow r = service.advance(1L, "核准备案");
        assertEquals(3, r.getStage());
        assertEquals(2, r.getStatus());
    }

    @Test
    void 回退一次只退一关不归零() {
        when(mapper.selectById(1L)).thenReturn(flow(2, 1));
        TGiprCaseFlow r = service.rollback(1L, "补材料");
        assertEquals(1, r.getStage()); // 旧实现无条件清零回 0
        assertEquals(1, r.getStatus());
    }

    @Test
    void 头关无处可退末关无处可推() {
        when(mapper.selectById(1L)).thenReturn(flow(0, 0));
        assertNull(service.rollback(1L, "x"));
        when(mapper.selectById(1L)).thenReturn(flow(3, 2));
        assertNull(service.advance(1L, "x"));
    }

    @Test
    void 已封住的单子冻结不得改删退() {
        when(mapper.selectById(1L)).thenReturn(flow(3, 2));
        assertNull(service.advance(1L, "x"));
        assertNull(service.rollback(1L, "x"));
        assertFalse(service.updateContent(1L, "改"));
        assertFalse(service.remove(1L));
    }

    @Test
    void 删除为软删() {
        when(mapper.selectById(1L)).thenReturn(flow(1, 1));
        when(mapper.updateById(any())).thenReturn(1);
        service.remove(1L);
        org.mockito.ArgumentCaptor<TGiprCaseFlow> cap = org.mockito.ArgumentCaptor.forClass(TGiprCaseFlow.class);
        verify(mapper).updateById(cap.capture());
        assertEquals(1, (int) cap.getValue().getDelFlag());
        verify(mapper, org.mockito.Mockito.never()).deleteById(any());
    }
}
