package com.fc.v2.service.impl;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** 按批次号串行化提交：并发下同批只认头一遭的结果 */
    private static final ConcurrentHashMap<String, Object> BATCH_LOCKS = new ConcurrentHashMap<String, Object>();

    @javax.annotation.Resource
    private TGiprArchiveRowMapper giprArchiveRowMapper;

    @Override
    public TGiprArchiveRow selectTGiprArchiveRowById(Long id) {
        return this.giprArchiveRowMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TGiprArchiveRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (rows.size() > MAX_ROWS) {
            return -1;
        }
        String no = batchNo == null || batchNo.trim().isEmpty()
                ? rows.get(0).getBatchNo() : batchNo.trim();
        if (no == null || no.trim().isEmpty()) {
            return 0;
        }
        Object lock = BATCH_LOCKS.computeIfAbsent(no, k -> new Object());
        synchronized (lock) {
            // 幂等：同一批次重复提交不得重复入库，只认头一遭算出来的结果
            List<TGiprArchiveRow> existing = this.giprArchiveRowMapper.selectList(
                    new QueryWrapper<TGiprArchiveRow>().eq("batch_no", no));
            if (!existing.isEmpty()) {
                int done = 0;
                for (TGiprArchiveRow r : existing) {
                    if (r.getStatus() != null && r.getStatus() == STATUS_OK) {
                        done++;
                    }
                }
                return done;
            }
            int ok = 0;
            int seq = 0;
            for (TGiprArchiveRow r : rows) {
                seq++;
                r.setRowNo(Integer.valueOf(seq));
                r.setBatchNo(no);
                boolean valid = r.getItemCode() != null && !r.getItemCode().trim().isEmpty()
                        && r.getQty() != null
                        && r.getQty().compareTo(java.math.BigDecimal.ZERO) > 0;
                // 合法的入库记收下，非法的记入失败明细（保留原始行号），整批同一事务落库
                r.setStatus(valid ? STATUS_OK : STATUS_FAIL);
                this.giprArchiveRowMapper.insert(r);
                if (valid) {
                    ok++;
                }
            }
            return ok;
        }
    }

    @Override
    public List<TGiprArchiveRow> listErrors(String batchNo) {
        return this.giprArchiveRowMapper.selectList(new QueryWrapper<TGiprArchiveRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL).orderByAsc("row_no"));
    }
}
