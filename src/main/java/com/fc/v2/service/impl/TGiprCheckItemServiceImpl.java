package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TGiprCheckItemMapper;
import com.fc.v2.model.auto.TGiprCheckItem;
import com.fc.v2.service.ITGiprCheckItemService;

/**
 * 年度核查与续展提醒条目 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * 统一口径：
 * - 只取未删、还搁着(status=0)、到期日 due_at 不为空的条目；
 * - 到期压界归"可处理"：至多许提前 amount 日，最早可处理时刻 = due_at - amount 天；
 *   at 等于该时刻（或 due_at 当日）即可处理（闭区间，不再把恰好到期判成未到期）；
 *   amount 为空/负数按 0 天（只能当天及以后）。
 * - runOnce 逐条发：发出落 status=1，发不成落 status=2，单条失败跳过继续，返回成功条数。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGiprCheckItemServiceImpl implements ITGiprCheckItemService {

    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;
    private static final int NOT_DELETED = 0;
    private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;

    @javax.annotation.Resource
    private TGiprCheckItemMapper giprCheckItemMapper;

    @Override
    public TGiprCheckItem selectTGiprCheckItemById(Long id) {
        if (id == null) {
            return null;
        }
        TGiprCheckItem r = this.giprCheckItemMapper.selectById(id);
        if (r != null && r.getDelFlag() != null && r.getDelFlag() != NOT_DELETED) {
            return null;
        }
        return r;
    }

    @Override
    public List<TGiprCheckItem> listDue(Date at) {
        List<TGiprCheckItem> due = new java.util.ArrayList<TGiprCheckItem>();
        if (at == null) {
            return due;
        }
        List<TGiprCheckItem> all = this.giprCheckItemMapper.selectList(new QueryWrapper<TGiprCheckItem>()
                .eq("del_flag", NOT_DELETED)
                .eq("status", STATUS_WAIT));
        for (TGiprCheckItem r : all) {
            if (r.getDueAt() != null && !at.before(earliestAt(r))) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        List<TGiprCheckItem> due = listDue(at);
        // 空集合直接 0，不再 get(0) 空指针
        int ok = 0;
        for (TGiprCheckItem r : due) {
            try {
                r.setStatus(STATUS_DONE);
                int rows = this.giprCheckItemMapper.updateById(r);
                if (rows > 0) {
                    ok++;
                }
            } catch (RuntimeException ex) {
                // 单条失败：记发不成后跳过，继续其余条目
                try {
                    r.setStatus(STATUS_FAIL);
                    this.giprCheckItemMapper.updateById(r);
                } catch (RuntimeException ignored) {
                    // 连失败标记都落不下时也不再打断整批
                }
            }
        }
        return ok;
    }

    /** 最早可处理时刻 = 到期日 - 至多提前天数；提前天数为空/非正按 0 */
    private Date earliestAt(TGiprCheckItem r) {
        long advanceDays = 0L;
        if (r.getAmount() != null && r.getAmount().signum() > 0) {
            advanceDays = r.getAmount().longValue();
        }
        return new Date(r.getDueAt().getTime() - advanceDays * MILLIS_PER_DAY);
    }
}
