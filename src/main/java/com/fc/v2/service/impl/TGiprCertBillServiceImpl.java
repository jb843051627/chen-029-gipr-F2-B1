package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TGiprCertBillMapper;
import com.fc.v2.model.auto.TGiprCertBill;
import com.fc.v2.service.ITGiprCertBillService;

/**
 * 专用标志使用授权签批单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGiprCertBillServiceImpl implements ITGiprCertBillService {

    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TGiprCertBillMapper giprCertBillMapper;

    @Override
    public TGiprCertBill selectTGiprCertBillById(Long id) {
        return this.giprCertBillMapper.selectById(id);
    }

    @Override
    public TGiprCertBill approve(Long id, String approver, String comment) {
        TGiprCertBill r = this.giprCertBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        int status = r.getStatus() == null ? STATUS_RUNNING : r.getStatus();
        if (status != STATUS_RUNNING) {
            return null;
        }
        int signed = (r.getSignCount() == null ? 0 : r.getSignCount()) + 1;
        r.setSignCount(Integer.valueOf(signed));
        // 本关算齐办法：0一名即齐，1两名须同判（按应签人数齐票）
        int mode = r.getSignMode() == null ? MODE_OR : r.getSignMode();
        int need = r.getNeedCount() == null || r.getNeedCount() < 1 ? 1 : r.getNeedCount();
        boolean nodeDone = mode == MODE_AND ? signed >= need : signed >= 1;
        if (nodeDone) {
            int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
            if (node >= MAX_NODE) {
                r.setStatus(Integer.valueOf(STATUS_PASS));
            } else {
                r.setNodeNo(Integer.valueOf(node + 1));
                r.setSignCount(Integer.valueOf(0));
                r.setStatus(Integer.valueOf(STATUS_RUNNING));
            }
        }
        appendTrace(r, approver, comment);
        this.giprCertBillMapper.updateById(r);
        return r;
    }

    @Override
    public TGiprCertBill reject(Long id, String approver, String comment) {
        TGiprCertBill r = this.giprCertBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int status = r.getStatus() == null ? STATUS_RUNNING : r.getStatus();
        if (status != STATUS_RUNNING) {
            return null;
        }
        r.setStatus(Integer.valueOf(STATUS_VETO));
        appendTrace(r, approver, comment);
        this.giprCertBillMapper.updateById(r);
        return r;
    }

    @Override
    public TGiprCertBill rollback(Long id, String comment) {
        TGiprCertBill r = this.giprCertBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int status = r.getStatus() == null ? STATUS_RUNNING : r.getStatus();
        if (status == STATUS_VETO) {
            return null;
        }
        // 退回上一环节（与流转单同一口径：逐档退）：本关已收票清零，单子回到在签
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        r.setNodeNo(Integer.valueOf(Math.max(0, node - 1)));
        r.setSignCount(Integer.valueOf(0));
        r.setStatus(Integer.valueOf(STATUS_RUNNING));
        appendTrace(r, null, comment);
        this.giprCertBillMapper.updateById(r);
        return r;
    }

    /** 签批留痕：谁办的、说了什么，追加进备注（超长截断） */
    private static void appendTrace(TGiprCertBill r, String approver, String comment) {
        StringBuilder trace = new StringBuilder();
        if (r.getRemark() != null && !r.getRemark().isEmpty()) {
            trace.append(r.getRemark()).append(';');
        }
        if (approver != null && !approver.trim().isEmpty()) {
            trace.append(approver.trim());
        }
        if (comment != null && !comment.trim().isEmpty()) {
            trace.append(':').append(comment.trim());
        }
        String remark = trace.toString();
        if (remark.length() > 500) {
            remark = remark.substring(remark.length() - 500);
        }
        r.setRemark(remark.isEmpty() ? r.getRemark() : remark);
    }
}
