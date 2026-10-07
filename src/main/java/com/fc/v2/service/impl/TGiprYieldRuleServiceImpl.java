package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
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
 * 全模块唯一口径（申报页 / 后台 / 列表角标共用，两条求值路径走同一套判定）：
 * 1) 可用三条件（缺一不可）：del_flag=0、status=0（在位，撤下的线不混进生效集）、
 *    at 落在生效窗口内：eff_start &lt;= at &lt; eff_end。eff_end 注释“交班那一一日(不含)”，
 *    故 at=eff_end 归失效（压界归失效侧）；eff_start/eff_end 为 null 表示该侧无界。
 *    窗口在应用层比较，不走 JDBC 日期参数，避开 serverTimezone 偏移。
 * 2) 分档：阈值升序（th1&lt;=th2&lt;=th3）；<b>等于上限归高一档</b>——
 *    input==th1 归 2 档、input==th2 归 3 档；th3 是封顶，input==th3 归 3 档。
 * 3) 量程：输入必须 &gt;=0 且 &lt;=该线封顶限值 th3_max；越界（异常大）或 th3 未维护一律返回 0。
 * 4) 选线次序：priority 降序 → rule_code 降序 → id 降序，全程确定，
 *    同一时刻多条线可用时与库返回排列无关；同优先级两次求值必得同一档位（幂等）。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGiprYieldRuleServiceImpl implements ITGiprYieldRuleService {

    /** 线的情形：0在位 1已撤下 */
    private static final int STATUS_ACTIVE = 0;
    /** 不可求值标记（无生效版本 / 输入越界 / 参数缺失 / 未知编号） */
    private static final int INVALID = 0;

    /**
     * 选线次序：优先级降序 → 代号降序 → id 降序。
     */
    private static final Comparator<TGiprYieldRule> SELECT_ORDER = new Comparator<TGiprYieldRule>() {
        @Override
        public int compare(TGiprYieldRule a, TGiprYieldRule b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return Integer.compare(pb, pa);
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            int byCode = cb.compareTo(ca);
            if (byCode != 0) {
                return byCode;
            }
            long ia = a.getId() == null ? 0L : a.getId();
            long ib = b.getId() == null ? 0L : b.getId();
            return Long.compare(ib, ia);
        }
    };

    @javax.annotation.Resource
    private TGiprYieldRuleMapper giprYieldRuleMapper;

    @Override
    public TGiprYieldRule selectTGiprYieldRuleById(Long id) {
        if (id == null) {
            return null;
        }
        TGiprYieldRule r = this.giprYieldRuleMapper.selectById(id);
        // 已逻辑删除的不当作存在；在位/窗口口径由 usable(id, at) 判定
        if (r != null && r.getDelFlag() != null && r.getDelFlag() != 0) {
            return null;
        }
        return r;
    }

    @Override
    public List<TGiprYieldRule> listAvailable(Date at) {
        List<TGiprYieldRule> avail = new ArrayList<TGiprYieldRule>();
        if (at == null) {
            return avail;
        }
        List<TGiprYieldRule> all = this.giprYieldRuleMapper.selectList(
                new QueryWrapper<TGiprYieldRule>().eq("del_flag", 0));
        for (TGiprYieldRule r : all) {
            if (isUsable(r, at)) {
                avail.add(r);
            }
        }
        avail.sort(SELECT_ORDER);
        return avail;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return INVALID;
        }
        TGiprYieldRule rule = selectActiveVersion(ruleCode, at);
        // 编号没建过 / 该时刻无生效版本：返回 0，不抛空指针
        return grade(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return INVALID;
        }
        List<TGiprYieldRule> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return INVALID;
        }
        // 与 evaluate 同一个分档/量程函数，口径一致
        return grade(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TGiprYieldRule r = this.giprYieldRuleMapper.selectById(id);
        return r != null && isUsable(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    /** 同一代号在 at 时刻可用的版本中，按统一选线次序取头一条 */
    private TGiprYieldRule selectActiveVersion(String ruleCode, Date at) {
        List<TGiprYieldRule> hit = this.giprYieldRuleMapper.selectList(new QueryWrapper<TGiprYieldRule>()
                .eq("del_flag", 0)
                .eq("rule_code", ruleCode));
        TGiprYieldRule picked = null;
        for (TGiprYieldRule r : hit) {
            if (!isUsable(r, at)) {
                continue;
            }
            if (picked == null || SELECT_ORDER.compare(r, picked) < 0) {
                picked = r;
            }
        }
        return picked;
    }

    /**
     * 可用统一谓词：在位 + 生效窗口（起 &lt;= at &lt; 止）。
     * del_flag 由查询条件保证，这里只判 status 与窗口，供全部入口复用。
     */
    private boolean isUsable(TGiprYieldRule r, Date at) {
        if (r == null || at == null) {
            return false;
        }
        if (r.getStatus() == null || r.getStatus() != STATUS_ACTIVE) {
            return false;
        }
        if (r.getEffStart() != null && at.before(r.getEffStart())) {
            return false;
        }
        if (r.getEffEnd() != null && !at.before(r.getEffEnd())) {
            // 交班日不含：at == effEnd 即失效
            return false;
        }
        return true;
    }

    /**
     * 量程校验 + 分档，两条求值路径唯一入口。
     * 量程：0 &lt;= input &lt;= th3（封顶限值）；th3 未维护无法判档，返回 0。
     * 分档（等于上限归高一档）：
     * input &lt; th1 → 1；th1 &lt;= input &lt; th2 → 2；th2 &lt;= input &lt;= th3 → 3。
     * th1/th2 未维护时跳过该界限。
     */
    private int grade(TGiprYieldRule rule, BigDecimal input) {
        if (rule == null) {
            return INVALID;
        }
        BigDecimal cap = rule.getTh3Max();
        if (cap == null || input.signum() < 0 || input.compareTo(cap) > 0) {
            return INVALID;
        }
        BigDecimal th1 = rule.getTh1Max();
        BigDecimal th2 = rule.getTh2Max();
        if (th1 != null && input.compareTo(th1) < 0) {
            return 1;
        }
        if (th2 != null && input.compareTo(th2) < 0) {
            return 2;
        }
        return 3;
    }
}
