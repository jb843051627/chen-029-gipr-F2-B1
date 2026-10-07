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
 * 统一口径（与签批单的退回同为"一步"，归档/回退两处一致）：
 * - 推进：每次只过一关 stage+1，到末关(3)即封住(status=2)；
 * - 回退：每次只退一关 stage-1（floor 0），不再无条件清零；
 * - 已封住(status=2)的单子冻结：推进/回退/改内容/删除一律拒绝。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGiprCaseFlowServiceImpl implements ITGiprCaseFlowService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_READY = 0;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;
    private static final int NOT_DELETED = 0;

    @javax.annotation.Resource
    private TGiprCaseFlowMapper giprCaseFlowMapper;

    @Override
    public TGiprCaseFlow selectTGiprCaseFlowById(Long id) {
        if (id == null) {
            return null;
        }
        TGiprCaseFlow r = this.giprCaseFlowMapper.selectById(id);
        if (r != null && r.getDelFlag() != null && r.getDelFlag() != NOT_DELETED) {
            return null;
        }
        return r;
    }

    @Override
    public List<TGiprCaseFlow> selectTGiprCaseFlowList(QueryWrapper<TGiprCaseFlow> queryWrapper) {
        QueryWrapper<TGiprCaseFlow> wrapper = queryWrapper == null ? new QueryWrapper<TGiprCaseFlow>() : queryWrapper;
        wrapper.eq("del_flag", NOT_DELETED);
        wrapper.orderByAsc("id");
        return this.giprCaseFlowMapper.selectList(wrapper);
    }

    @Override
    public TGiprCaseFlow advance(Long id, String remark) {
        TGiprCaseFlow r = loadActive(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        if (st >= MAX_STAGE) {
            // 已在末关封住，不能再推
            return null;
        }
        int next = st + 1;
        r.setStage(next);
        r.setStatus(next >= MAX_STAGE ? STATUS_TERMINAL : STATUS_ACTIVE);
        r.setLastAction(remark);
        this.giprCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public TGiprCaseFlow rollback(Long id, String remark) {
        TGiprCaseFlow r = loadActive(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        if (st <= 0) {
            // 已在头关，无处可退
            return null;
        }
        r.setStage(st - 1);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.giprCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TGiprCaseFlow r = loadActive(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.giprCaseFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TGiprCaseFlow r = loadActive(id);
        if (r == null) {
            return false;
        }
        r.setDelFlag(1);
        return this.giprCaseFlowMapper.updateById(r) > 0;
    }

    /** 取未删且未封住的在办单；已封住的单子任何改动都被拒 */
    private TGiprCaseFlow loadActive(Long id) {
        if (id == null) {
            return null;
        }
        TGiprCaseFlow r = this.giprCaseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        if (r.getDelFlag() != null && r.getDelFlag() != NOT_DELETED) {
            return null;
        }
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return null;
        }
        return r;
    }
}
