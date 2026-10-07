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
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        int next = Math.min(st + 1, MAX_STAGE);
        r.setStage(next);
        r.setStatus(next >= MAX_STAGE ? STATUS_TERMINAL : STATUS_ACTIVE);
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
        int st = r.getStage() == null ? 0 : r.getStage();
        // 回退一档（与签批单同一口径：逐档退，不直接清零）；已封住的退回即在办
        r.setStage(Math.max(st - 1, 0));
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
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
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
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return false;
        }
        return this.giprCaseFlowMapper.deleteById(id) > 0;
    }

}
