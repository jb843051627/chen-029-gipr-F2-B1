package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TGiprCertBillMapper;
import com.fc.v2.model.auto.TGiprCertBill;
import com.fc.v2.service.ITGiprCertBillService;

/**
 * 专用标志使用授权签批单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * 统一口径：
 * - 关次 node_no 0..2（县初核 / 市核认 / 省核准）；
 * - 本关算齐：sign_mode=0 一名即齐(1 票)，sign_mode=1 须同判 need_count 人；
 *   签到齐数才放过本关、node+1 进入下一关并清零本关已签数；省局(第 2 关)签齐即点齐(status=1)。
 * - 否决即压回(status=2)；退回上一环节 node-1（与办件流转单的回退同为一步，口径一致）。
 * - 已点齐/已压回(status=1/2)的单子冻结，任何签批/否决/退回都拒绝。
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
    private static final int NOT_DELETED = 0;

    @javax.annotation.Resource
    private TGiprCertBillMapper giprCertBillMapper;

    @Override
    public TGiprCertBill selectTGiprCertBillById(Long id) {
        if (id == null) {
            return null;
        }
        TGiprCertBill r = this.giprCertBillMapper.selectById(id);
        if (r != null && r.getDelFlag() != null && r.getDelFlag() != NOT_DELETED) {
            return null;
        }
        return r;
    }

    @Override
    public TGiprCertBill approve(Long id, String approver, String comment) {
        TGiprCertBill r = loadRunning(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node > MAX_NODE) {
            return null;
        }
        int signed = r.getSignCount() == null ? 0 : r.getSignCount();
        signed++;
        int quorum = quorumOf(r);
        if (signed < quorum) {
            // 本关还没签齐，停在本关继续等签
            r.setSignCount(signed);
            this.giprCertBillMapper.updateById(r);
            return r;
        }
        // 本关签齐：放过，清零已签数进入下一关
        r.setSignCount(0);
        if (node >= MAX_NODE) {
            r.setNodeNo(MAX_NODE);
            r.setStatus(STATUS_PASS);
        } else {
            r.setNodeNo(node + 1);
            r.setStatus(STATUS_RUNNING);
        }
        this.giprCertBillMapper.updateById(r);
        return r;
    }

    @Override
    public TGiprCertBill reject(Long id, String approver, String comment) {
        TGiprCertBill r = loadRunning(id);
        if (r == null) {
            return null;
        }
        r.setStatus(STATUS_VETO);
        this.giprCertBillMapper.updateById(r);
        return r;
    }

    @Override
    public TGiprCertBill rollback(Long id, String comment) {
        TGiprCertBill r = loadRunning(id);
        if (r == null) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node <= 0) {
            return null;
        }
        // 退回上一环节（一步），本关已签数作废
        r.setNodeNo(node - 1);
        r.setSignCount(0);
        r.setStatus(STATUS_RUNNING);
        this.giprCertBillMapper.updateById(r);
        return r;
    }

    /** 本关算齐所需票数：一名即齐=1；两名同判=need_count（缺省按 2） */
    private int quorumOf(TGiprCertBill r) {
        if (r.getSignMode() != null && r.getSignMode() == MODE_AND) {
            return r.getNeedCount() != null && r.getNeedCount() > 0 ? r.getNeedCount() : 2;
        }
        return 1;
    }

    private TGiprCertBill loadRunning(Long id) {
        if (id == null) {
            return null;
        }
        TGiprCertBill r = this.giprCertBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        if (r.getDelFlag() != null && r.getDelFlag() != NOT_DELETED) {
            return null;
        }
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        return r;
    }
}
