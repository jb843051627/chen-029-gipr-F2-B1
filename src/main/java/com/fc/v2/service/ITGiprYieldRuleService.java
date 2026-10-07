package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TGiprYieldRule;

/**
 * 核定产量与特色指标换算贴线档 Service接口（rule-eval 形状：规则求值，无增删改）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITGiprYieldRuleService {

    /** 按主键查询规则 */
    TGiprYieldRule selectTGiprYieldRuleById(Long id);

    /**
     * 单规则求值：按 ruleCode 取 at 时刻生效的版本（在位 + 起&lt;=at&lt;止）判档。
     * 档位：input&lt;th1 为 1 档，th1&lt;=input&lt;th2 为 2 档，th2&lt;=input&lt;=th3 为 3 档，
     * 即等于上限归高一档。无生效版本 / 编号不存在 / 输入越界（&lt;0 或 &gt;th3）/ 参数缺失一律返回 0。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** 多规则求值：at 时刻可用规则中按 priority 降序(并列 ruleCode、id 降序)取头一条，
     *  用与 {@link #evaluate} 完全相同的口径判档；无可用规则返回 0 */
    int evaluateTop(BigDecimal input, Date at);

    /** at 时刻可用规则（priority 降序 → ruleCode 降序 → id 降序，确定性次序） */
    List<TGiprYieldRule> listAvailable(Date at);

    /** 单条规则在 at 时刻是否可用（批量导入/报表等旁路复用，口径须与定位/列表一致） */
    boolean usable(Long id, Date at);

    /** at 时刻可用规则条数（列表页角标 / 看板） */
    int countAvailable(Date at);
}
