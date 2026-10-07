package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TGiprArchiveRowMapper;
import com.fc.v2.model.auto.TGiprArchiveRow;
import com.fc.v2.service.ITGiprArchiveRowService;

/**
 * 历史用标卷宗转档核收行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGiprArchiveRowServiceImpl implements ITGiprArchiveRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TGiprArchiveRowMapper giprArchiveRowMapper;

    @Override
    public TGiprArchiveRow selectTGiprArchiveRowById(Long id) {
        return this.giprArchiveRowMapper.selectById(id);
    }

    @Override
    public int submitBatch(String batchNo, List<TGiprArchiveRow> rows) {
        String no = rows.get(0).getBatchNo();
        java.util.List<TGiprArchiveRow> errors = new java.util.ArrayList<TGiprArchiveRow>();
        int seq = 0;
        for (TGiprArchiveRow r : rows) {
            if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                seq++;
                r.setRowNo(Integer.valueOf(seq));
                r.setBatchNo(no);
                r.setStatus(STATUS_FAIL);
                this.giprArchiveRowMapper.insert(r);
                errors.add(r);
            }
        }
        if (!errors.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TGiprArchiveRow r : rows) {
            r.setBatchNo(no);
            r.setStatus(STATUS_OK);
            this.giprArchiveRowMapper.insert(r);
            ok++;
        }
        return ok;
    }

    @Override
    public List<TGiprArchiveRow> listErrors(String batchNo) {
        return this.giprArchiveRowMapper.selectList(new QueryWrapper<TGiprArchiveRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL));
    }
}
