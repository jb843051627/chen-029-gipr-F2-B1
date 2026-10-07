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
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGiprCheckItemServiceImpl implements ITGiprCheckItemService {

    /** 提前量量程：至多许提前几日只认 [2,5]，量程外的数视同没填（按默认 5 日） */
    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TGiprCheckItemMapper giprCheckItemMapper;

    @Override
    public TGiprCheckItem selectTGiprCheckItemById(Long id) {
        return this.giprCheckItemMapper.selectById(id);
    }

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /** 本条提前量（日）：amount 量程 [2,5]，未填或越程按默认 WIN_TO */
    private static int effDays(TGiprCheckItem r) {
        if (r.getAmount() == null) {
            return WIN_TO;
        }
        int days = r.getAmount().intValue();
        if (days < WIN_FROM || days > WIN_TO) {
            return WIN_TO;
        }
        return days;
    }

    @Override
    public List<TGiprCheckItem> listDue(Date at) {
        List<TGiprCheckItem> due = new java.util.ArrayList<TGiprCheckItem>();
        if (at == null) {
            return due;
        }
        // 尚未处理 + 未删除；到没到期在墙钟口径下逐条核
        List<TGiprCheckItem> all = this.giprCheckItemMapper.selectList(new QueryWrapper<TGiprCheckItem>()
                .eq("del_flag", 0).eq("status", STATUS_WAIT).orderByAsc("due_at"));
        String now = ts(at);
        for (TGiprCheckItem r : all) {
            if (r.getDueAt() == null) {
                continue;
            }
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(r.getDueAt());
            cal.add(java.util.Calendar.DAY_OF_YEAR, -effDays(r));
            // 执行窗口 [到期日 - 提前量, 到期日]：到点可发，过早过晚都不收
            if (ts(cal.getTime()).compareTo(now) <= 0 && ts(r.getDueAt()).compareTo(now) >= 0) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        int ok = 0;
        for (TGiprCheckItem r : listDue(at)) {
            try {
                r.setStatus(Integer.valueOf(STATUS_DONE));
                if (this.giprCheckItemMapper.updateById(r) > 0) {
                    ok++;
                    continue;
                }
            } catch (RuntimeException e) {
                // 单条失败跳过继续，不拖垮整趟
            }
            try {
                r.setStatus(Integer.valueOf(STATUS_FAIL));
                this.giprCheckItemMapper.updateById(r);
            } catch (RuntimeException ignore) {
                // 记失败本身再失败只能放下，等下一趟
            }
        }
        return ok;
    }
}
