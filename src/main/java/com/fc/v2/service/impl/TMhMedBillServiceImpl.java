package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TMhMedBillMapper;
import com.fc.v2.model.auto.TMhMedBill;
import com.fc.v2.service.ITMhMedBillService;

/**
 * 特殊用药签核单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMhMedBillServiceImpl implements ITMhMedBillService {

    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TMhMedBillMapper mhMedBillMapper;

    @Override
    public TMhMedBill selectTMhMedBillById(Long id) {
        return this.mhMedBillMapper.selectById(id);
    }

    @Override
    public TMhMedBill approve(Long id, String approver, String comment) {
        if (id == null) {
            return null;
        }
        if (approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (approver.trim().length() > 32) {
            return null;
        }
        TMhMedBill r = findRunning(id);
        if (r == null) {
            return null;
        }
        // 结构合法性校验：环节越界/应签人数非正/票数越界 一律视为脏数据，不予签批
        if (!sane(r)) {
            return null;
        }
        int mode = resolveMode(r);
        int need = resolveNeed(r);
        int cnt = vote(r);
        if (mode == MODE_AND && cnt >= need) {
            // 会签已满但上一轮推进被中断：本次补推进，不再重复计票
            advance(r);
            this.mhMedBillMapper.updateById(r);
            return r;
        }
        cnt = cnt + 1;
        r.setSignCount(Integer.valueOf(cnt));
        if (mode == MODE_AND && cnt < need) {
            // 会签未满：只累加票数，环节原地不动，等后续签批人补齐
            this.mhMedBillMapper.updateById(r);
            return r;
        }
        // 或签一票即推进；会签满员推进（推进/结案口径唯一出处）
        advance(r);
        this.mhMedBillMapper.updateById(r);
        return r;
    }

    @Override
    public TMhMedBill reject(Long id, String approver, String comment) {
        if (id == null) {
            return null;
        }
        if (approver == null || approver.trim().isEmpty()) {
            return null;
        }
        TMhMedBill r = findRunning(id);
        if (r == null) {
            // 已结案（已通过/已否决）或不存在：不得再否决
            return null;
        }
        if (!sane(r)) {
            return null;
        }
        // 否决即终止整条链：状态置否决并作废本轮签署，后续任何环节都不得再签
        r.setStatus(Integer.valueOf(STATUS_VETO));
        r.setSignCount(Integer.valueOf(0));
        this.mhMedBillMapper.updateById(r);
        return r;
    }

    @Override
    public TMhMedBill rollback(Long id, String comment) {
        if (id == null) {
            return null;
        }
        if (comment == null || comment.trim().isEmpty()) {
            return null;
        }
        TMhMedBill r = findRunning(id);
        if (r == null) {
            return null;
        }
        if (!sane(r)) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        int prev = node - 1;
        if (prev < 0) {
            // 已在首环节：退回原地，绝不产生负环节（脏数据会把链彻底走死）
            prev = 0;
        }
        // 退回即作废本轮签署：上一轮签过的票一律清零，重看后重新计票
        r.setNodeNo(Integer.valueOf(prev));
        r.setSignCount(Integer.valueOf(0));
        this.mhMedBillMapper.updateById(r);
        return r;
    }
    /** 统一入口守卫：不存在或已结案（已通过/已否决）一律返回 null，三个入口同一出处 */
    private TMhMedBill findRunning(Long id) {
        if (id == null) {
            return null;
        }
        TMhMedBill r = this.mhMedBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        if (r.getStatus() == null) {
            // 存量脏数据：状态缺失按"在途"处理，交由 sane() 再做结构校验
            return r;
        }
        if (r.getStatus().intValue() != STATUS_RUNNING) {
            return null;
        }
        return r;
    }

    /** 结构校验：环节越界、应签人数非正、票数为负或超过应签数，均视为脏数据不予签批 */
    private boolean sane(TMhMedBill r) {
        if (r == null) {
            return false;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node < 0 || node > MAX_NODE) {
            return false;
        }
        int need = r.getNeedCount() == null ? 1 : r.getNeedCount();
        if (need <= 0) {
            return false;
        }
        int cnt = r.getSignCount() == null ? 0 : r.getSignCount();
        if (cnt < 0 || cnt > need) {
            return false;
        }
        return true;
    }

    /** 签批模式解析：缺省按或签（单人签批）处理，未知取值也归或签，避免链卡死 */
    private int resolveMode(TMhMedBill r) {
        if (r.getSignMode() == null) {
            return MODE_OR;
        }
        int m = r.getSignMode().intValue();
        return (m == MODE_AND) ? MODE_AND : MODE_OR;
    }

    /** 应签人数解析：缺省按 1 人；非正值按 1 人兜底，杜绝"签 0 票即过" */
    private int resolveNeed(TMhMedBill r) {
        if (r.getNeedCount() == null) {
            return 1;
        }
        int n = r.getNeedCount().intValue();
        return n > 0 ? n : 1;
    }

    /** 当前票数：缺省 0；负值按 0 计（脏数据不放大票数） */
    private int vote(TMhMedBill r) {
        if (r.getSignCount() == null) {
            return 0;
        }
        int c = r.getSignCount().intValue();
        return c > 0 ? c : 0;
    }

    /** 推进环节：未到末环节则进入下一环节并清空本轮票数；末环节之后置为已通过且不再越界 */
    private void advance(TMhMedBill r) {
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        int next = node + 1;
        if (next > MAX_NODE) {
            r.setStatus(Integer.valueOf(STATUS_PASS));
            r.setSignCount(Integer.valueOf(0));
            return;
        }
        r.setNodeNo(Integer.valueOf(next));
        r.setSignCount(Integer.valueOf(0));
    }

}
