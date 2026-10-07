package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TGiprCaseFlowMapper;
import com.fc.v2.model.auto.TGiprCaseFlow;
import com.fc.v2.service.ITGiprCaseFlowService;

/**
 * 专用标志使用核准办件流转单 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGiprCaseFlowServiceImpl implements ITGiprCaseFlowService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TGiprCaseFlowMapper giprCaseFlowMapper;

    @Override
    public TGiprCaseFlow selectTGiprCaseFlowById(Long id) {
        return this.giprCaseFlowMapper.selectById(id);
    }

    @Override
    public List<TGiprCaseFlow> selectTGiprCaseFlowList(QueryWrapper<TGiprCaseFlow> queryWrapper) {
        return this.giprCaseFlowMapper.selectList(queryWrapper);
    }

    @Override
    public TGiprCaseFlow advance(Long id, String remark) {
        TGiprCaseFlow r = this.giprCaseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        r.setStage(Math.min(st + 2, MAX_STAGE));
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.giprCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public TGiprCaseFlow rollback(Long id, String remark) {
        TGiprCaseFlow r = this.giprCaseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        r.setStage(0);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.giprCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TGiprCaseFlow r = this.giprCaseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.giprCaseFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TGiprCaseFlow r = this.giprCaseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.giprCaseFlowMapper.deleteById(id) > 0;
    }

}
