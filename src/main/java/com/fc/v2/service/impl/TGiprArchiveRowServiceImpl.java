package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TGiprArchiveRowMapper;
import com.fc.v2.model.auto.TGiprArchiveRow;
import com.fc.v2.service.ITGiprArchiveRowService;

/**
 * 历史用标卷宗转档核收行 Service业务层处理（batch-process 形状：整批提交）
 *
 * 统一口径：
 * - 空批次返回 0；行数超单批上限(500)返回 -1 且整批不入库；
 * - 同一批次码重复提交不重复入库（幂等），直接回此前成功行数；
 * - 逐行校验：授权册代号非空、本行卷数 qty&gt;0；合法行落"已收下"(1)，
 *   非法行落"已退回"(2) 并保留提交行号；一笔事务内完成，不留半截子；
 * - 返回成功（已收下）行数；失败明细按行号升序回查。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGiprArchiveRowServiceImpl implements ITGiprArchiveRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;
    private static final int NOT_DELETED = 0;

    @javax.annotation.Resource
    private TGiprArchiveRowMapper giprArchiveRowMapper;

    @Override
    public TGiprArchiveRow selectTGiprArchiveRowById(Long id) {
        if (id == null) {
            return null;
        }
        TGiprArchiveRow r = this.giprArchiveRowMapper.selectById(id);
        if (r != null && r.getDelFlag() != null && r.getDelFlag() != NOT_DELETED) {
            return null;
        }
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TGiprArchiveRow> rows) {
        // 空批次：不碰库
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        String no = batchNo == null ? null : batchNo.trim();
        if (no.isEmpty()) {
            // 批次码缺失则退回，整批不收
            return -1;
        }
        // 超上限：整批不入库
        if (rows.size() > MAX_ROWS) {
            return -1;
        }
        // 批次幂等：已交过的批次不重复入库，回此前成功行数
        Integer existed = this.giprArchiveRowMapper.selectCount(new QueryWrapper<TGiprArchiveRow>()
                .eq("batch_no", no).eq("status", STATUS_OK));
        if (existed != null && existed > 0) {
            return existed;
        }

        int seq = 0;
        int ok = 0;
        for (TGiprArchiveRow r : rows) {
            seq++;
            if (r.getRowNo() == null) {
                r.setRowNo(seq);
            }
            r.setBatchNo(no);
            if (r.getDelFlag() == null) {
                r.setDelFlag(NOT_DELETED);
            }
            if (isValid(r)) {
                r.setStatus(STATUS_OK);
                ok++;
            } else {
                r.setStatus(STATUS_FAIL);
            }
            // 合法、非法都只落这一次；成功行数与失败明细同批一笔事务
            this.giprArchiveRowMapper.insert(r);
        }
        return ok;
    }

    @Override
    public List<TGiprArchiveRow> listErrors(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return new java.util.ArrayList<TGiprArchiveRow>();
        }
        return this.giprArchiveRowMapper.selectList(new QueryWrapper<TGiprArchiveRow>()
                .eq("batch_no", batchNo)
                .eq("status", STATUS_FAIL)
                .eq("del_flag", NOT_DELETED)
                .orderByAsc("row_no", "id"));
    }

    private boolean isValid(TGiprArchiveRow r) {
        return r.getItemCode() != null && !r.getItemCode().trim().isEmpty()
                && r.getQty() != null
                && r.getQty().compareTo(BigDecimal.ZERO) > 0;
    }
}
