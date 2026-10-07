package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TGiprProductBookMapper;
import com.fc.v2.mapper.auto.TGiprTermBookMapper;
import com.fc.v2.model.auto.TGiprProductBook;
import com.fc.v2.model.auto.TGiprTermBook;

/**
 * 名录词条册单测：列表/详情口径统一、lack 轧差、ver_no 乐观更新头一遭赢、
 * 量程校验、底册联动、软删。
 */
@ExtendWith(MockitoExtension.class)
class TGiprTermBookServiceImplTest {

    @Mock
    private TGiprTermBookMapper baseMapper;

    @Mock
    private TGiprProductBookMapper productMapper;

    @InjectMocks
    private TGiprTermBookServiceImpl service;

    @Captor
    private ArgumentCaptor<TGiprTermBook> entityCaptor;

    private TGiprProductBook product(int status) {
        TGiprProductBook p = new TGiprProductBook();
        p.setId(0L);
        p.setProdNo("GP00");
        p.setProdName("浚浦蟹");
        p.setStatus(status);
        p.setDelFlag(0);
        return p;
    }

    private TGiprTermBook term() {
        TGiprTermBook t = new TGiprTermBook();
        t.setProdId(0);
        t.setTermName("汉文正名");
        t.setCrtNum(5);
        t.setHitNum(2);
        return t;
    }

    @Test
    void 列表使用调用方条件并统一追加未删与版本排序() {
        QueryWrapper<TGiprTermBook> qw = new QueryWrapper<TGiprTermBook>().eq("status", 0);
        when(baseMapper.selectList(any())).thenReturn(Collections.<TGiprTermBook>emptyList());

        service.selectTGiprTermBookList(qw);

        ArgumentCaptor<QueryWrapper<TGiprTermBook>> cap = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(baseMapper).selectList(cap.capture());
        String seg = cap.getValue().getSqlSegment();
        // 调用方的在位条件没被丢弃
        assertTrue(seg.contains("status"), seg);
        // service 统一兜底未删
        assertTrue(seg.contains("del_flag"), seg);
        // 版本新的在前，次序确定
        assertTrue(cap.getValue().getSqlSegment().toUpperCase().contains("ORDER BY"), seg);
    }

    @Test
    void 撤下条目详情仍可取_状态口径与列表同源() {
        TGiprTermBook off = term();
        off.setId(7L);
        off.setStatus(1);
        when(baseMapper.selectOne(any())).thenReturn(off);
        // 详情不暗藏 status=0，撤下与否由调用方显式决定，两页看到同一条
        assertEquals(7L, service.selectTGiprTermBookById(7L).getId());
    }

    @Test
    void 新增轧算lack为应收减已对齐并回写底册() {
        when(productMapper.selectOne(any())).thenReturn(product(0));
        when(baseMapper.selectCount(any())).thenReturn(0);
        when(baseMapper.insert(any())).thenReturn(1);

        TGiprTermBook t = term(); // crt5 hit2
        assertEquals(1, service.insertTGiprTermBook(t));

        verify(baseMapper).insert(entityCaptor.capture());
        TGiprTermBook saved = entityCaptor.getValue();
        assertEquals(3, saved.getLackNum());            // 5-2
        assertEquals("GP00", saved.getProdNo());        // 底册联动
        assertEquals("浚浦蟹", saved.getProdName());
        assertEquals(0, saved.getStatus());              // 默认在位
        assertEquals(1, saved.getVerNo());               // 首版
        assertEquals(0, saved.getDelFlag());
    }

    @Test
    void 已对齐大于应收时lack轧平为0不为负() {
        when(productMapper.selectOne(any())).thenReturn(product(0));
        when(baseMapper.selectCount(any())).thenReturn(0);
        when(baseMapper.insert(any())).thenReturn(1);
        TGiprTermBook t = term();
        t.setCrtNum(1);
        t.setHitNum(4);

        service.insertTGiprTermBook(t);
        verify(baseMapper).insert(entityCaptor.capture());
        assertEquals(0, (int) entityCaptor.getValue().getLackNum());
    }

    @Test
    void 两人同时改只认头一遭写入后来者被拒() {
        when(productMapper.selectOne(any())).thenReturn(product(0));

        TGiprTermBook first = term();
        first.setId(1L);
        first.setVerNo(1); // 两人都读到了 ver_no=1
        when(baseMapper.update(any(), any())).thenReturn(1);
        assertEquals(1, service.updateTGiprTermBook(first));
        verify(baseMapper).update(entityCaptor.capture(), any());
        assertEquals(2, entityCaptor.getValue().getVerNo()); // 头一遭写进 ver_no=2

        TGiprTermBook second = term();
        second.setId(1L);
        second.setVerNo(1); // 仍拿旧版次来改
        when(baseMapper.update(any(), any())).thenReturn(0); // 条件 ver_no=1 已不命中
        assertEquals(0, service.updateTGiprTermBook(second));
    }

    @Test
    void 更新重新轧算lack并回写底册() {
        when(productMapper.selectOne(any())).thenReturn(product(0));
        when(baseMapper.update(any(), any())).thenReturn(1);
        TGiprTermBook t = term();
        t.setId(1L);
        t.setVerNo(2);
        t.setCrtNum(10);
        t.setHitNum(8);

        service.updateTGiprTermBook(t);
        verify(baseMapper).update(entityCaptor.capture(), any());
        TGiprTermBook saved = entityCaptor.getValue();
        assertEquals(2, saved.getLackNum());
        assertEquals("GP00", saved.getProdNo());
        assertEquals("浚浦蟹", saved.getProdName());
        assertNull(saved.getTermNo(), "term_no 是跨版本身份，不随修改改动");
    }

    @Test
    void langNum量程外视同没填_termKind越界整笔拒() {
        when(productMapper.selectOne(any())).thenReturn(product(0));
        when(baseMapper.selectCount(any())).thenReturn(0);
        when(baseMapper.insert(any())).thenReturn(1);
        TGiprTermBook t = term();
        t.setLangNum(9); // 量程 1..3 之外
        t.setTermKind(5); // 只收 0/1/2
        assertEquals(0, service.insertTGiprTermBook(t));
        verify(baseMapper, never()).insert(any());

        TGiprTermBook t2 = term();
        t2.setLangNum(0);
        service.insertTGiprTermBook(t2);
        verify(baseMapper).insert(entityCaptor.capture());
        assertNull(entityCaptor.getValue().getLangNum());
    }

    @Test
    void 名录已移出则词条不收() {
        when(productMapper.selectOne(any())).thenReturn(product(1));
        assertEquals(0, service.insertTGiprTermBook(term()));
        verify(baseMapper, never()).insert(any());
    }

    @Test
    void 删除为软删不物理删除() {
        when(baseMapper.update(any(), any())).thenReturn(1);
        assertEquals(1, service.deleteTGiprTermBookById(9L));
        verify(baseMapper).update(entityCaptor.capture(), any());
        assertEquals(1, (int) entityCaptor.getValue().getDelFlag());
        verify(baseMapper, never()).deleteById(any());
    }
}
