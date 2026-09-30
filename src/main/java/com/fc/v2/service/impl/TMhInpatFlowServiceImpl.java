package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMhInpatFlowMapper;
import com.fc.v2.model.auto.TMhInpatFlow;
import com.fc.v2.service.ITMhInpatFlowService;

/**
 * 住院处置单 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMhInpatFlowServiceImpl implements ITMhInpatFlowService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;
    /** 待发起 */
    private static final int STATUS_PENDING = 0;
    /** 备注长度上限：超长文本会撑坏台账列表 */
    private static final int MAX_REMARK = 200;
    /** 环节中文名：留痕消息给运维看的是环节名而不是裸数字 */
    private static final String[] STAGE_NAMES = {"受理", "计量", "复核", "办结"};

    /** 被拒/已流转的留痕（单机内存缓冲，供排查"点了没反应"的线上投诉） */
    private final java.util.Map<Long, String> rejectTrace =
            new java.util.concurrent.ConcurrentHashMap<Long, String>();

    @javax.annotation.Resource
    private TMhInpatFlowMapper mhInpatFlowMapper;

    @Override
    public TMhInpatFlow selectTMhInpatFlowById(Long id) {
        if (id == null) {
            return null;
        }
        return this.mhInpatFlowMapper.selectById(id);
    }

    @Override
    public List<TMhInpatFlow> selectTMhInpatFlowList(QueryWrapper<TMhInpatFlow> queryWrapper) {
        QueryWrapper<TMhInpatFlow> q = (queryWrapper == null) ? new QueryWrapper<TMhInpatFlow>() : queryWrapper;
        // 台账只列未删除单（软删标记与其它模块口径一致，避免已删单混进流转台账）
        q.eq("del_flag", 0);
        List<TMhInpatFlow> rows = this.mhInpatFlowMapper.selectList(q);
        return rows == null ? new java.util.ArrayList<TMhInpatFlow>() : rows;
    }

    @Override
    public TMhInpatFlow advance(Long id, String remark) {
        if (id == null) {
            return null;
        }
        TMhInpatFlow r = this.mhInpatFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        // ① 终态口径统一：先做状态归一，脏数据（环节已到末档但 status 仍是"在办"）按已办结处理
        if (isTerminal(r)) {
            note(id, "已办结单据不得再流转");
            return null;
        }
        // ② 环节越界：末环节本应已是终态；漏置状态的历史脏数据先纠正再拒，避免"点一次动一点"
        int st = stageOf(r);
        if (st >= MAX_STAGE) {
            r.setStage(MAX_STAGE);
            r.setStatus(STATUS_TERMINAL);
            stamp(r);
            persist(r);
            note(id, "已到末环节，自动补办结");
            return null;
        }
        // ③ 单据头必备字段：无编号单不允许流转（否则台账出现无法追溯的单）
        if (!headerOk(r)) {
            note(id, "单据编号缺失，不允许流转");
            return null;
        }
        // ④ 防连点：同一动作在同一环节连续发起直接拒绝（双击/网络重试是常态）
        String action = remarkOf(remark);
        if (isRepeated(r, action)) {
            note(id, "同一动作连续发起，已忽略");
            return null;
        }
        // ⑤ 正常推进一档：状态由环节反推，保证 stage 与 status 永远自洽
        r.setStage(st + 1);
        r.setStatus(statusOfStage(st + 1));
        r.setLastAction(action);
        stamp(r);
        if (!persist(r)) {
            note(id, "流转落库失败");
            return null;
        }
        note(id, "推进至" + stageName(r.getStage()));
        return r;
    }

    @Override
    public TMhInpatFlow rollback(Long id, String remark) {
        if (id == null) {
            return null;
        }
        TMhInpatFlow r = this.mhInpatFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        // ① 终态与 advance 同一口径：已办结单据不得回退
        if (isTerminal(r)) {
            note(id, "已办结单据不得回退");
            return null;
        }
        // ② 首环节不可回退（再退就是负数，会破坏 stage 与 status 的自洽）
        int st = stageOf(r);
        if (st <= 0) {
            note(id, "已到首环节，无法回退");
            return null;
        }
        if (!headerOk(r)) {
            note(id, "单据编号缺失，不允许回退");
            return null;
        }
        // ③ 防连点：回退同样受"同一动作不连续"约束
        String action = remarkOf(remark);
        if (isRepeated(r, action)) {
            note(id, "同一动作连续发起，已忽略");
            return null;
        }
        // ④ 回退只退一档：回退是"撤销上一步"，不是"打回起点"
        r.setStage(st - 1);
        r.setStatus(statusOfStage(st - 1));
        r.setLastAction(action);
        stamp(r);
        if (!persist(r)) {
            note(id, "回退落库失败");
            return null;
        }
        note(id, "退回至" + stageName(r.getStage()));
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TMhInpatFlow r = (id == null) ? null : this.mhInpatFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // ① 终态守卫：与 advance/rollback 同一口径（办结单内容已入档，改了就与归档件不一致）
        if (isTerminal(r)) {
            note(id, "已办结单据不得修改内容");
            return false;
        }
        // ② 内容归一：去首尾空白并截断，null 与空串统一折成空串（列表页表现才一致）
        String content = remarkOf(remark);
        // ③ 幂等：内容没变不再写库，避免 update_time 无意义漂移
        if (content.equals(r.getContent() == null ? "" : r.getContent())) {
            return true;
        }
        r.setContent(content);
        stamp(r);
        return persist(r);
    }

    @Override
    public boolean remove(Long id) {
        TMhInpatFlow r = (id == null) ? null : this.mhInpatFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // ① 终态守卫：已办结单据长期留档，任何入口都不得删除
        if (isTerminal(r)) {
            note(id, "已办结单据不得删除");
            return false;
        }
        // ② 删除前留痕：记录删除时所在环节，便于审计追溯
        note(id, "删除单据，原环节=" + stageName(r.getStage()));
        boolean removed = this.mhInpatFlowMapper.deleteById(id) > 0;
        if (!removed) {
            note(id, "删除失败，单据可能已被他人处理");
        }
        return removed;
    }

    /** 单据头必备字段：业务编号不能缺，否则台账出现无法追溯的单 */
    private boolean headerOk(TMhInpatFlow r) {
        return r.getBizNo() != null && !r.getBizNo().trim().isEmpty();
    }

    /** 终态口径：先做 status 归一，再判是否已办结 */
    private boolean isTerminal(TMhInpatFlow r) {
        return normalizeStatus(r) == STATUS_TERMINAL;
    }

    /** 状态归一：null→待发起，越界值钳到合法区间（写库时由 statusOfStage 保证与环节自洽） */
    private int normalizeStatus(TMhInpatFlow r) {
        int status = r.getStatus() == null ? STATUS_PENDING : r.getStatus().intValue();
        if (status <= STATUS_PENDING) {
            return STATUS_PENDING;
        }
        if (status >= STATUS_TERMINAL) {
            return STATUS_TERMINAL;
        }
        return STATUS_ACTIVE;
    }

    /** 环节归一：null→0，负数与超末档一律钳到合法区间，避免脏数据把流转推飞 */
    private int stageOf(TMhInpatFlow r) {
        int st = r.getStage() == null ? 0 : r.getStage().intValue();
        if (st < 0) {
            return 0;
        }
        if (st > MAX_STAGE) {
            return MAX_STAGE;
        }
        return st;
    }

    /** 由环节反推状态：走到末环节即终态，否则在办 */
    private int statusOfStage(int stage) {
        return stage >= MAX_STAGE ? STATUS_TERMINAL : STATUS_ACTIVE;
    }

    /** 备注/内容归一：去首尾空白 + 截断上限，null 统一折成空串 */
    private String remarkOf(String remark) {
        if (remark == null) {
            return "";
        }
        String s = remark.trim();
        if (s.length() > MAX_REMARK) {
            s = s.substring(0, MAX_REMARK);
        }
        return s;
    }

    /** 连续同动作判定：仅当本次动作非空且与上次完全一致时视为重复（防连点） */
    private boolean isRepeated(TMhInpatFlow r, String action) {
        if (action == null || action.isEmpty()) {
            return false;
        }
        return action.equals(r.getLastAction());
    }

    /** 环节名（留痕用）：越界环节按末环节名兜底 */
    private static String stageName(Integer stage) {
        int st = stage == null ? 0 : stage.intValue();
        if (st < 0 || st >= STAGE_NAMES.length) {
            st = STAGE_NAMES.length - 1;
        }
        return STAGE_NAMES[st];
    }

    /** 统一落库：失败交由调用方按"被拒"处理（不允许静默吞掉） */
    private boolean persist(TMhInpatFlow r) {
        return this.mhInpatFlowMapper.updateById(r) > 0;
    }

    /** 写前盖章：软删标记缺省补 0，更新时间统一由服务端生成 */
    private static void stamp(TMhInpatFlow r) {
        if (r.getDelFlag() == null) {
            r.setDelFlag(0);
        }
        r.setUpdateTime(new java.util.Date());
    }

    /** 流转留痕：成功与被拒都写同一条内存缓冲，便于排查"最后一步到底动没动" */
    private void note(Long id, String what) {
        if (id == null) {
            return;
        }
        if (rejectTrace.size() > 500) {
            rejectTrace.clear();
        }
        rejectTrace.put(id, what);
    }

}
