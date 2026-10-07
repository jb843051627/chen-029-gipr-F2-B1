package com.fc.v2.service.impl;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TGiprTermBookMapper;
import com.fc.v2.mapper.auto.TGiprProductBookMapper;
import com.fc.v2.model.auto.TGiprTermBook;
import com.fc.v2.model.auto.TGiprProductBook;
import com.fc.v2.service.ITGiprTermBookService;
import com.fc.v2.util.SnowflakeIdWorker;
import com.fc.v2.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 名录对外公示多语言词条册Service业务层处理
 *
 * 统一口径（名录页 / 审核页 / 后台共用）：
 * - 列表与详情都只以 del_flag=0 为底线过滤，在位/撤下(status) 由调用方显式传参，
 *   不再出现"列表有、详情是另一条"或两页判定不一；同 term_no 多版本按 ver_no 降序，
 *   数值大的先说话，点哪条看哪条（按 id 取）。
 * - 分页完全交给 Controller 的 startPage()，service 不再硬编码页码。
 * - lack_num 一律轧算：应收 crt_num - 已对齐 hit_num（不为负），新增/更新同口径，不留填数空位。
 * - prod_no/prod_name 以名录底册为准，新增与更新都回写，两处联动。
 * - 并发更新走 ver_no 乐观条件：同一条两人同时改，头一个写进 ver_no+1，
 *   后到者条件不命中被拒（返回 0），须重读再改——只认头一遭落库的内容。
 * - lang_num 量程 1..3，量程外视同没填（置 null）；term_kind 仅收 0/1/2。
 * - 删除为软删（del_flag=1），不物理 DELETE。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TGiprTermBookServiceImpl extends ServiceImpl<TGiprTermBookMapper, TGiprTermBook> implements ITGiprTermBookService {

    /** 行的情形：0在位 1已撤下 */
    private static final int STATUS_ACTIVE = 0;
    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;

    @org.springframework.beans.factory.annotation.Autowired
    private TGiprProductBookMapper giprProductBookMapper;

    @Override
    public TGiprTermBook selectTGiprTermBookById(Long id) {
        if (id == null) {
            return null;
        }
        // 与列表同一底线：只过滤 del_flag，撤下条目能否看见由各页显式状态口径决定
        return this.baseMapper.selectOne(new QueryWrapper<TGiprTermBook>()
                .eq("id", id)
                .eq("del_flag", NOT_DELETED));
    }

    @Override
    public List<TGiprTermBook> selectTGiprTermBookList(Wrapper<TGiprTermBook> queryWrapper) {
        QueryWrapper<TGiprTermBook> wrapper;
        if (queryWrapper instanceof QueryWrapper) {
            wrapper = (QueryWrapper<TGiprTermBook>) queryWrapper;
        } else {
            wrapper = new QueryWrapper<TGiprTermBook>();
        }
        // 统一兜底：未删；版本新的在前，保证列表次序确定、与库返回排列无关
        wrapper.eq("del_flag", NOT_DELETED);
        wrapper.orderByDesc("ver_no", "id");
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTGiprTermBook(TGiprTermBook record) {
        if (record == null) {
            return 0;
        }
        TGiprProductBook refArch = resolveProduct(record.getProdId());
        if (refArch == null) {
            return 0;
        }

        // term_no 由系统排上，不留手填的格子；手填值也只作建议，空则发号
        String termNo = StringUtils.isNotEmpty(record.getTermNo()) ? record.getTermNo().trim() : null;
        if (termNo == null) {
            termNo = "TM" + SnowflakeIdWorker.getUUID();
            record.setTermNo(termNo);
        }
        Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TGiprTermBook>()
                .eq("term_no", termNo).eq("del_flag", NOT_DELETED));
        if (dupCnt != null && dupCnt > 0) {
            return 0;
        }

        if (!normalizeAndValidate(record)) {
            return 0;
        }

        // 两处联动：冗余代号/全称一律以名录底册为准
        record.setProdNo(refArch.getProdNo());
        record.setProdName(refArch.getProdName());
        record.setLackNum(computeLack(record));

        if (record.getStatus() == null) {
            record.setStatus(STATUS_ACTIVE);
        }
        if (record.getVerNo() == null) {
            record.setVerNo(1);
        }
        record.setDelFlag(NOT_DELETED);
        return this.baseMapper.insert(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTGiprTermBook(TGiprTermBook record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        // 并发幂等：必须带着读到的 ver_no 来改；头一遭写入后 ver_no+1，后来者条件不命中
        if (record.getVerNo() == null) {
            return 0;
        }
        int baseVer = record.getVerNo();

        TGiprProductBook refArch = resolveProduct(record.getProdId());
        if (refArch == null) {
            // 不允许把词条挂到不存在/已移出/已删的名录产品上
            return 0;
        }
        if (!normalizeAndValidate(record)) {
            return 0;
        }

        // term_no 是跨版本身份，不随修改改动
        record.setTermNo(null);
        // 底册联动回写
        record.setProdNo(refArch.getProdNo());
        record.setProdName(refArch.getProdName());
        record.setLackNum(computeLack(record));
        record.setUpdateTime(new Date());
        record.setVerNo(baseVer + 1);

        int affected = this.baseMapper.update(record, new UpdateWrapper<TGiprTermBook>()
                .eq("id", record.getId())
                .eq("del_flag", NOT_DELETED)
                .eq("ver_no", baseVer));
        return affected > 0 ? affected : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTGiprTermBookByIds(String ids) {
        if (StringUtils.isEmpty(ids)) {
            return 0;
        }
        Long[] idArr = ConvertUtil.toLongArray(ids);
        TGiprTermBook tomb = new TGiprTermBook();
        tomb.setDelFlag(DELETED);
        tomb.setUpdateTime(new Date());
        return this.baseMapper.update(tomb, new UpdateWrapper<TGiprTermBook>()
                .in("id", Arrays.asList(idArr))
                .eq("del_flag", NOT_DELETED));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTGiprTermBookById(Long id) {
        if (id == null) {
            return 0;
        }
        TGiprTermBook tomb = new TGiprTermBook();
        tomb.setDelFlag(DELETED);
        tomb.setUpdateTime(new Date());
        return this.baseMapper.update(tomb, new UpdateWrapper<TGiprTermBook>()
                .eq("id", id)
                .eq("del_flag", NOT_DELETED));
    }

    /** 取在册名录产品：存在、未删、未移出(status=1)。prodId 缺失视为不合法 */
    private TGiprProductBook resolveProduct(Integer prodId) {
        if (prodId == null) {
            return null;
        }
        TGiprProductBook ref = giprProductBookMapper.selectOne(new QueryWrapper<TGiprProductBook>()
                .eq("id", prodId).eq("del_flag", NOT_DELETED));
        if (ref == null) {
            return null;
        }
        if (ref.getStatus() != null && ref.getStatus() == 1) {
            return null;
        }
        return ref;
    }

    /** 量程/取值规整与校验：不合法返回 false（整笔不收） */
    private boolean normalizeAndValidate(TGiprTermBook record) {
        // 语种条数量程 1..3，量程外视同没填
        if (record.getLangNum() != null && (record.getLangNum() < 1 || record.getLangNum() > 3)) {
            record.setLangNum(null);
        }
        // 取用三式 0/1/2
        if (record.getTermKind() != null && (record.getTermKind() < 0 || record.getTermKind() > 2)) {
            return false;
        }
        // 生效窗口：起不能晚于止
        if (record.getEffStart() != null && record.getEffEnd() != null
                && record.getEffEnd().before(record.getEffStart())) {
            return false;
        }
        return true;
    }

    /** 还差几条 = 应收 - 已对齐，轧平不为负 */
    private int computeLack(TGiprTermBook record) {
        int crt = record.getCrtNum() == null ? 0 : record.getCrtNum();
        int hit = record.getHitNum() == null ? 0 : record.getHitNum();
        return Math.max(0, crt - hit);
    }
}
