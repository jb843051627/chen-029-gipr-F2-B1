package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TGiprYieldRuleMapper;
import com.fc.v2.model.auto.TGiprYieldRule;
import com.fc.v2.service.ITGiprYieldRuleService;

/**
 * 核定产量与特色指标换算贴线档 Service业务层处理（rule-eval 形状）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGiprYieldRuleServiceImpl implements ITGiprYieldRuleService {

    /** 量程：输入超出 [0,100] 一律按不可用处理（返回 0），各求值入口同一把尺 */
    private static final BigDecimal INPUT_MIN = BigDecimal.ZERO;
    private static final BigDecimal INPUT_MAX = new BigDecimal("100");

    /** 优先级降序；并列按 ruleCode 降序（确定性） */
    private static final Comparator<TGiprYieldRule> PRIORITY_THEN_CODE_DESC = new Comparator<TGiprYieldRule>() {
        @Override
        public int compare(TGiprYieldRule a, TGiprYieldRule b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TGiprYieldRuleMapper giprYieldRuleMapper;

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）+ 字符串绑定：
     * JDBC 的 serverTimezone 与本机时区不对称，直接把 java.util.Date 作参数会整体偏移，
     * 使边界判定在"切换当日"这类用例上随机错档（实测踩到）。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /**
     * 可用判据（所有入口同一把尺）：未删除 + 在位 + at 落在生效区间
     * [eff_start, eff_end) 内；起止留空视为该头不设限。
     */
    private static QueryWrapper<TGiprYieldRule> availableWrapper(Date at) {
        String now = ts(at);
        return new QueryWrapper<TGiprYieldRule>()
                .eq("del_flag", 0)
                .eq("status", 0)
                .and(w -> w.isNull("eff_start").or().le("eff_start", now))
                .and(w -> w.isNull("eff_end").or().gt("eff_end", now));
    }

    /** 单条记录是否满足可用判据（与 availableWrapper 同口径，墙钟字符串比较） */
    private static boolean isAvailable(TGiprYieldRule r, Date at) {
        if (r == null || at == null) {
            return false;
        }
        if (r.getDelFlag() == null || r.getDelFlag() != 0) {
            return false;
        }
        if (r.getStatus() == null || r.getStatus() != 0) {
            return false;
        }
        String now = ts(at);
        if (r.getEffStart() != null && ts(r.getEffStart()).compareTo(now) > 0) {
            return false;
        }
        if (r.getEffEnd() != null && ts(r.getEffEnd()).compareTo(now) <= 0) {
            return false;
        }
        return true;
    }

    /** 量程校验：越界输入不得出档 */
    private static boolean inRange(BigDecimal input) {
        return input != null
                && input.compareTo(INPUT_MIN) >= 0
                && input.compareTo(INPUT_MAX) <= 0;
    }

    @Override
    public TGiprYieldRule selectTGiprYieldRuleById(Long id) {
        return this.giprYieldRuleMapper.selectById(id);
    }

    @Override
    public List<TGiprYieldRule> listAvailable(Date at) {
        if (at == null) {
            return new java.util.ArrayList<TGiprYieldRule>();
        }
        List<TGiprYieldRule> avail = this.giprYieldRuleMapper.selectList(availableWrapper(at));
        avail.sort(PRIORITY_THEN_CODE_DESC);
        return avail;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || at == null) {
            return 0;
        }
        if (!inRange(input)) {
            return 0;
        }
        TGiprYieldRule rule = findRule(ruleCode, at);
        if (rule == null) {
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (at == null || !inRange(input)) {
            return 0;
        }
        List<TGiprYieldRule> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TGiprYieldRule r = this.giprYieldRuleMapper.selectById(id);
        return isAvailable(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    /**
     * 定位 at 时刻生效的版本：可用判据 + ruleCode 收敛后，按统一次序取头一条；
     * 没有生效版本返回 null（调用方按不可用处理）。
     */
    private TGiprYieldRule findRule(String ruleCode, Date at) {
        List<TGiprYieldRule> hit = this.giprYieldRuleMapper.selectList(availableWrapper(at)
                .eq("rule_code", ruleCode));
        if (hit.isEmpty()) {
            return null;
        }
        hit.sort(PRIORITY_THEN_CODE_DESC);
        return hit.get(0);
    }

    /** 分档：等于上限取高一档（压界归高一头，各入口同一口径） */
    private int levelOf(TGiprYieldRule rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) < 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) < 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }
}
