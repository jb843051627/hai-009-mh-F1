package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMhHolidayMapper;
import com.fc.v2.mapper.auto.TMhRegHandoverMapper;
import com.fc.v2.mapper.auto.TMhRegTaskMapper;
import com.fc.v2.mapper.auto.TMhScanRoundMapper;
import com.fc.v2.mapper.auto.TMhScanWindowMapper;
import com.fc.v2.mapper.auto.TMhWaitLineMapper;
import com.fc.v2.model.auto.TMhHoliday;
import com.fc.v2.model.auto.TMhRegHandover;
import com.fc.v2.model.auto.TMhRegTask;
import com.fc.v2.model.auto.TMhScanRound;
import com.fc.v2.model.auto.TMhScanWindow;
import com.fc.v2.model.auto.TMhWaitLine;
import com.fc.v2.service.ITMhRegTaskService;

/**
 * 号源到期转号事项条 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * 口径汇总（改一处先读这里）：
 * - 浮出：surface = due_at - amount 个自然日，浮出日当天即该扫；只认 status=待转。
 * - 窗口：法定节假日整段不扫；日常档只看每日起止时刻；流感专档还要落在适用日期段内。
 *   自动点算时专档每日时刻命中即走专档；专档适用日期内（含非专档钟点）日常档整天不开，
 *   窗口外到期的号原样等下轮窗口。
 * - 排队：候补来路(0)整体排在普通挂号(1)前，同来路按挂队列时刻先后；
 *   号超期满三个自然日的事项整单提到本轮最前；普通挂号不许插队。
 * - 转成：事项 status 待转→已转 与 trans_at 在同一事务同生共死，候补条同步置已接号；
 *   任何一样没落上整笔回滚，该号仍算待转，下轮照当待转翻出。
 * - 流感专档：同一档(win_code)同一号只转一次（交接留痕为凭）；当天二次进队列直接忽略。
 * - 挂起：医生休诊/科室停摆（suspend 入口）或候补整队列撤了，就地挂起写原由，不拖整轮。
 * - 撤号/改用途：只追加留痕；release_at / trans_at 两个时刻任何路径都不覆盖。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMhRegTaskServiceImpl implements ITMhRegTaskService {

    /** 事项情形 */
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;
    private static final int STATUS_HOLD = 3;
    private static final int STATUS_CANCEL = 4;
    private static final int STATUS_REPURPOSE = 5;

    /** 候补来路 / 排队情形 */
    private static final int QUEUE_WAITLIST = 0;
    private static final int QUEUE_NORMAL = 1;
    private static final int QS_WAITING = 0;
    private static final int QS_PICKED = 1;
    private static final int QS_WITHDRAWN = 2;

    /** 窗口档别 / 窗口情形 */
    private static final int WIN_KIND_DAY = 0;
    private static final int WIN_KIND_FLU = 1;
    private static final int WIN_ACTIVE = 0;

    /** 交接动作 */
    private static final int ACT_TRANSFER = 1;
    private static final int ACT_SUSPEND = 2;
    private static final int ACT_CANCEL = 3;
    private static final int ACT_REPURPOSE = 4;

    /** 轮次情形 */
    private static final int ROUND_IDLE = 0;
    private static final int ROUND_OK = 1;
    private static final int ROUND_ERR = 2;

    /** 号超期满三个自然日还转不出去：整单提到本轮最前 */
    private static final int OVERDUE_DAYS = 3;
    /** 日常白天档种子代号 */
    public static final String DAY_WIN_CODE = "DAY";

    private static final int MAX_REASON = 250;

    @Resource
    private TMhRegTaskMapper mhRegTaskMapper;
    @Resource
    private TMhWaitLineMapper mhWaitLineMapper;
    @Resource
    private TMhScanWindowMapper mhScanWindowMapper;
    @Resource
    private TMhHolidayMapper mhHolidayMapper;
    @Resource
    private TMhRegHandoverMapper mhRegHandoverMapper;
    @Resource
    private TMhScanRoundMapper mhScanRoundMapper;

    /** 转号事务：单元裸装（无 Spring 容器）时允许为 null，此时按 CAS 条件更新保证原子语义 */
    @Resource
    private PlatformTransactionManager transactionManager;

    /** 撤队原由内存留痕（与住院单 rejectTrace 同一手法，供挂起原由回查） */
    private final ConcurrentHashMap<Long, String> withdrawnTrace = new ConcurrentHashMap<Long, String>();
    /** 轮次序号：同秒连点两轮也不重号 */
    private static final AtomicLong ROUND_SEQ = new AtomicLong(0);

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    private static String dayKey(Date d) {
        return ts(d).substring(0, 10);
    }

    private static String timeKey(Date d) {
        return ts(d).substring(11);
    }

    /** 自然日平移（墙钟口径，与 ts() 同一时区假设） */
    private static Date plusDays(Date d, int days) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTime();
    }

    private static Date truncDay(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private static int naturalDays(BigDecimal amount) {
        if (amount == null) {
            return 0;
        }
        int n = amount.intValue();
        return n > 0 ? n : 0;
    }

    /**
     * 浮出日 = 截止日（墙钟自然日）往前挪 amount 个自然日，取浮出日 0 点整。
     * due_at 是"截止那一天最后一刻"，浮出从该日零点即生效，全天可扫（含当日）。
     */
    private static Date surfaceOf(TMhRegTask r) {
        return plusDays(truncDay(r.getDueAt()), -naturalDays(r.getAmount()));
    }

    private static String normReason(String reason) {
        if (reason == null) {
            return "";
        }
        String s = reason.trim();
        return s.length() > MAX_REASON ? s.substring(0, MAX_REASON) : s;
    }

    @Override
    public TMhRegTask selectTMhRegTaskById(Long id) {
        return id == null ? null : this.mhRegTaskMapper.selectById(id);
    }

    @Override
    public List<TMhRegTask> listDue(Date at) {
        if (at == null) {
            return new ArrayList<TMhRegTask>();
        }
        // SQL 侧先按 未删 + 待转 粗筛，浮出判定放内存（日期算术不下放 SQL，避免时区坑）
        List<TMhRegTask> all = this.mhRegTaskMapper.selectList(new QueryWrapper<TMhRegTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT));
        List<TMhRegTask> due = new ArrayList<TMhRegTask>();
        if (all == null) {
            return due;
        }
        String now = ts(at);
        for (TMhRegTask r : all) {
            if (r.getDueAt() != null && ts(surfaceOf(r)).compareTo(now) <= 0) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public ScanSettle runOnce(Date at) {
        return runOnce(at, null);
    }

    @Override
    public ScanSettle scanNow() {
        return runOnce(new Date(), null);
    }

    @Override
    public ScanSettle runOnce(Date at, String winCode) {
        String roundNo = newRoundNo(at, winCode);
        if (at == null) {
            return finishError(roundNo, winCode, "点算时刻缺失");
        }
        try {
            // ① 先定窗口：窗口外/节假日一律不动号，到期号也等下轮窗口
            TMhScanWindow win = resolveWindow(at, winCode);
            if (win == null) {
                return finishIdle(roundNo, null, 0, at);
            }
            // ② 同一次点算：候选快照只取这一轮，后面三个数全从这次遍历出
            List<TMhRegTask> due = listDue(at);
            if (due.isEmpty()) {
                // 窗口开着但真没有到期的号：明说无活。与"出错"严格分开，不许混报。
                return finishIdle(roundNo, win.getWinCode(), 0, at);
            }
            orderTasks(due, at);
            int done = 0;
            int hold = 0;
            for (TMhRegTask r : due) {
                try {
                    if (alreadyTransferredInWindow(r.getId(), win.getWinCode())) {
                        // 流感专档铁律：同一档同一号只转一次，先前那次原样保留
                        continue;
                    }
                    String withdrawn = withdrawnReason(r, at);
                    if (withdrawn != null) {
                        if (doSuspend(r, withdrawn, at, win.getWinCode(), roundNo)) {
                            hold++;
                        }
                        continue;
                    }
                    TMhWaitLine pick = pickWaiter(r.getId());
                    if (pick == null) {
                        // 队列排空或只有已撤队/已接号：没有承接人，号继续待转，不挂起不撤号
                        continue;
                    }
                    if (doTransfer(r, pick, at, win.getWinCode(), roundNo)) {
                        done++;
                    }
                } catch (RuntimeException ex) {
                    // 单条失败不拖整轮：该号仍算待转，下轮窗口再扫
                }
            }
            int waitCnt = due.size() - done - hold;
            return finishOk(roundNo, win.getWinCode(), due.size(), waitCnt, done, hold, at);
        } catch (RuntimeException ex) {
            return finishError(roundNo, winCode, ex.getMessage());
        }
    }

    @Override
    public TMhRegTask markRelease(Long regId, Date at) {
        if (regId == null || at == null) {
            return null;
        }
        TMhRegTask reg = this.mhRegTaskMapper.selectById(regId);
        if (reg == null || !safe(reg.getDelFlag())) {
            return null;
        }
        if (reg.getReleaseAt() != null) {
            // 最早放号日只落头一回：已有值原样返回，任何后续调用都不许覆盖
            return reg;
        }
        // 条件带上 release_at IS NULL：并发双放也只有一笔能落上
        int n = this.mhRegTaskMapper.update(null, new UpdateWrapper<TMhRegTask>()
                .eq("id", regId)
                .isNull("release_at")
                .eq("del_flag", 0)
                .set("release_at", at)
                .set("update_time", at));
        if (n == 1) {
            reg.setReleaseAt(at);
            reg.setUpdateTime(at);
        }
        return reg;
    }

    @Override
    public TMhWaitLine enqueue(Long regId, Long cardId, int queueType, Date at) {
        if (regId == null || cardId == null || at == null) {
            return null;
        }
        TMhRegTask reg = this.mhRegTaskMapper.selectById(regId);
        if (reg == null || !safe(reg.getDelFlag())) {
            return null;
        }
        if (reg.getStatus() == null || reg.getStatus().intValue() != STATUS_WAIT) {
            // 已转/挂起/撤号/改用途的号不再收候补
            return null;
        }
        // 当天二次进队列：同号同人同自然日（已接号/已撤队也算）直接忽略，先前那次原样保留
        Date joinDay = truncDay(at);
        List<TMhWaitLine> same = this.mhWaitLineMapper.selectList(new QueryWrapper<TMhWaitLine>()
                .eq("reg_id", regId)
                .eq("card_id", cardId)
                .eq("join_day", joinDay)
                .eq("del_flag", 0));
        if (same != null && !same.isEmpty()) {
            return null;
        }
        TMhWaitLine w = new TMhWaitLine();
        w.setRegId(regId);
        w.setCardId(cardId);
        w.setQueueType(queueType == QUEUE_NORMAL ? QUEUE_NORMAL : QUEUE_WAITLIST);
        w.setEnqueueAt(at);
        w.setJoinDay(joinDay);
        w.setQueueStatus(QS_WAITING);
        w.setDelFlag(0);
        w.setCreateTime(at);
        w.setUpdateTime(at);
        this.mhWaitLineMapper.insert(w);
        return w;
    }

    @Override
    public boolean withdrawQueue(Long regId, String reason) {
        if (regId == null) {
            return false;
        }
        String r = normReason(reason);
        Date now = new Date();
        int n = this.mhWaitLineMapper.update(null, new UpdateWrapper<TMhWaitLine>()
                .eq("reg_id", regId)
                .eq("queue_status", QS_WAITING)
                .eq("del_flag", 0)
                .set("queue_status", QS_WITHDRAWN)
                .set("remark", r)
                .set("update_time", now));
        if (n > 0) {
            this.withdrawnTrace.put(regId, r);
        }
        return n > 0;
    }

    @Override
    public TMhRegTask suspend(Long regId, String reason, Date at) {
        if (regId == null || at == null) {
            return null;
        }
        String r = normReason(reason);
        if (r.isEmpty()) {
            // 挂起必须写原由，没有原由的挂起不允许落库
            return null;
        }
        TMhRegTask reg = this.mhRegTaskMapper.selectById(regId);
        if (reg == null || !safe(reg.getDelFlag())) {
            return null;
        }
        if (reg.getStatus() != null && reg.getStatus().intValue() == STATUS_HOLD) {
            // 重复挂起幂等：原记录原样返回，不重复留痕
            return reg;
        }
        if (reg.getStatus() == null || reg.getStatus().intValue() != STATUS_WAIT) {
            // 已转/撤号/改用途终态不得再挂起
            return null;
        }
        try {
            return doSuspend(reg, r, at, null, null) ? reg : null;
        } catch (RuntimeException ex) {
            // 选后被并发改态：不留半条挂起痕，按未挂起返回
            return null;
        }
    }

    @Override
    public TMhRegTask cancel(Long regId, String reason, Date at) {
        return closeOut(regId, reason, at, STATUS_CANCEL, ACT_CANCEL);
    }

    @Override
    public TMhRegTask repurpose(Long regId, String reason, Date at) {
        return closeOut(regId, reason, at, STATUS_REPURPOSE, ACT_REPURPOSE);
    }

    // ------------------------------------------------------------------
    // 内部口径
    // ------------------------------------------------------------------

    /** 撤号/改用途同一出处：只能从待转(或历史"转不出去")收束；任何路径不碰两个时刻字段 */
    private TMhRegTask closeOut(Long regId, final String reason, final Date at,
                                final int targetStatus, final int actType) {
        if (regId == null || at == null) {
            return null;
        }
        final String r = normReason(reason);
        if (r.isEmpty()) {
            return null;
        }
        final TMhRegTask reg = this.mhRegTaskMapper.selectById(regId);
        if (reg == null || !safe(reg.getDelFlag())) {
            return null;
        }
        Integer st = reg.getStatus();
        if (st == null || (st.intValue() != STATUS_WAIT && st.intValue() != STATUS_FAIL)) {
            // 已转不可撤/改；已挂起走挂起口径，不混进撤号留痕
            return null;
        }
        final int[] updated = {0};
        try {
            executeAtomically(new Runnable() {
                @Override
                public void run() {
                    updated[0] = mhRegTaskMapper.update(null, new UpdateWrapper<TMhRegTask>()
                            .eq("id", regId)
                            .in("status", STATUS_WAIT, STATUS_FAIL)
                            .eq("del_flag", 0)
                            .set("status", targetStatus)
                            .set("update_time", at));
                    if (updated[0] != 1) {
                        throw new IllegalStateException("事项状态已变，放弃收束");
                    }
                    // 永不覆盖：set 清单里只有状态；release_at / trans_at 从不出场
                    writeHandover(reg, actType, null, null, at, r, null, at);
                }
            });
        } catch (RuntimeException ex) {
            return null;
        }
        reg.setStatus(targetStatus);
        reg.setUpdateTime(at);
        return reg;
    }

    /**
     * 窗口裁定：节假日→null（整段不扫）；指定代号只认该档；
     * 自动模式先看流感专档（日期段+每日时刻双命中），再落日常档。
     * 流感专档适用日期内，日常档整段不开（专档不走日常窗口，夜里也不许落回日常档）。
     */
    private TMhScanWindow resolveWindow(Date at, String winCode) {
        List<TMhScanWindow> wins = this.mhScanWindowMapper.selectList(new QueryWrapper<TMhScanWindow>()
                .eq("del_flag", 0)
                .eq("status", WIN_ACTIVE));
        if (wins == null || wins.isEmpty()) {
            return null;
        }
        if (isHoliday(at)) {
            return null;
        }
        TMhScanWindow fluPeriod = fluCoveringDate(wins, at);
        if (winCode != null) {
            for (TMhScanWindow w : wins) {
                if (!winCode.equals(w.getWinCode())) {
                    continue;
                }
                int kind = w.getWinKind() == null ? WIN_KIND_DAY : w.getWinKind().intValue();
                if (kind == WIN_KIND_DAY && fluPeriod != null) {
                    // 流感高峰那两周单开一档：显式点日常档也不开
                    return null;
                }
                return inWindow(w, at) ? w : null;
            }
            return null;
        }
        for (TMhScanWindow w : wins) {
            int kind = w.getWinKind() == null ? WIN_KIND_DAY : w.getWinKind().intValue();
            if (kind == WIN_KIND_FLU && inWindow(w, at)) {
                return w;
            }
        }
        if (fluPeriod != null) {
            // 专档适用日但不在专档每日时刻内：今日不开日常档，等下轮专档窗口
            return null;
        }
        for (TMhScanWindow w : wins) {
            int kind = w.getWinKind() == null ? WIN_KIND_DAY : w.getWinKind().intValue();
            if (kind == WIN_KIND_DAY && inWindow(w, at)) {
                return w;
            }
        }
        return null;
    }

    /** 流感专档是否覆盖该自然日（只看适用日期段，含两端） */
    private TMhScanWindow fluCoveringDate(List<TMhScanWindow> wins, Date at) {
        String d = dayKey(at);
        for (TMhScanWindow w : wins) {
            int kind = w.getWinKind() == null ? WIN_KIND_DAY : w.getWinKind().intValue();
            if (kind != WIN_KIND_FLU) {
                continue;
            }
            if (w.getDateFrom() != null && d.compareTo(dayKey(w.getDateFrom())) < 0) {
                continue;
            }
            if (w.getDateTo() != null && d.compareTo(dayKey(w.getDateTo())) > 0) {
                continue;
            }
            return w;
        }
        return null;
    }

    private boolean isHoliday(Date at) {
        List<TMhHoliday> holis = this.mhHolidayMapper.selectList(new QueryWrapper<TMhHoliday>()
                .eq("del_flag", 0));
        if (holis == null) {
            return false;
        }
        String d = dayKey(at);
        for (TMhHoliday h : holis) {
            if (h.getHoliDate() != null && dayKey(h.getHoliDate()).equals(d)) {
                return true;
            }
        }
        return false;
    }

    /** 档内判定：每日起止含起不含止；流感专档还要落在适用日期段（含两端）内 */
    private static boolean inWindow(TMhScanWindow w, Date at) {
        if (w.getDayFrom() == null || w.getDayTo() == null) {
            return false;
        }
        String t = timeKey(at);
        if (t.compareTo(timeKey(w.getDayFrom())) < 0 || t.compareTo(timeKey(w.getDayTo())) >= 0) {
            return false;
        }
        int kind = w.getWinKind() == null ? WIN_KIND_DAY : w.getWinKind().intValue();
        if (kind == WIN_KIND_FLU) {
            String d = dayKey(at);
            if (w.getDateFrom() != null && d.compareTo(dayKey(w.getDateFrom())) < 0) {
                return false;
            }
            if (w.getDateTo() != null && d.compareTo(dayKey(w.getDateTo())) > 0) {
                return false;
            }
        }
        return true;
    }

    /** 本轮事项次序：超期满三个自然日整单置顶；其余按浮出先后；再按 id 保证确定性 */
    private void orderTasks(List<TMhRegTask> due, final Date at) {
        final String today = dayKey(at);
        Collections.sort(due, new Comparator<TMhRegTask>() {
            @Override
            public int compare(TMhRegTask a, TMhRegTask b) {
                boolean oa = a.getDueAt() != null
                        && dayKey(plusDays(truncDay(a.getDueAt()), OVERDUE_DAYS)).compareTo(today) <= 0;
                boolean ob = b.getDueAt() != null
                        && dayKey(plusDays(truncDay(b.getDueAt()), OVERDUE_DAYS)).compareTo(today) <= 0;
                if (oa != ob) {
                    return oa ? -1 : 1;
                }
                int s = ts(surfaceOf(a)).compareTo(ts(surfaceOf(b)));
                if (s != 0) {
                    return s;
                }
                return Long.compare(a.getId() == null ? 0L : a.getId(),
                        b.getId() == null ? 0L : b.getId());
            }
        });
    }

    /** 候补整队列撤了：有条目且全部已撤队才算；还有在排的就照常往下转 */
    private String withdrawnReason(TMhRegTask r, Date at) {
        List<TMhWaitLine> rows = this.mhWaitLineMapper.selectList(new QueryWrapper<TMhWaitLine>()
                .eq("reg_id", r.getId())
                .eq("del_flag", 0));
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        String reason = null;
        for (TMhWaitLine w : rows) {
            int qs = w.getQueueStatus() == null ? QS_WAITING : w.getQueueStatus().intValue();
            if (qs != QS_WITHDRAWN) {
                return null;
            }
            if (reason == null && w.getRemark() != null && !w.getRemark().trim().isEmpty()) {
                reason = w.getRemark().trim();
            }
        }
        if (reason == null) {
            reason = this.withdrawnTrace.get(r.getId());
        }
        return reason == null || reason.isEmpty() ? "候补队列撤了" : reason;
    }

    /** 取队头：候补来路整体先于普通挂号（普通挂号不许插队），同来路挂得早的先转 */
    private TMhWaitLine pickWaiter(Long regId) {
        List<TMhWaitLine> rows = this.mhWaitLineMapper.selectList(new QueryWrapper<TMhWaitLine>()
                .eq("reg_id", regId)
                .eq("queue_status", QS_WAITING)
                .eq("del_flag", 0));
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Collections.sort(rows, new Comparator<TMhWaitLine>() {
            @Override
            public int compare(TMhWaitLine a, TMhWaitLine b) {
                int ta = a.getQueueType() == null ? QUEUE_WAITLIST : a.getQueueType().intValue();
                int tb = b.getQueueType() == null ? QUEUE_WAITLIST : b.getQueueType().intValue();
                if (ta != tb) {
                    return ta - tb;
                }
                Date ea = a.getEnqueueAt() == null ? new Date(0) : a.getEnqueueAt();
                Date eb = b.getEnqueueAt() == null ? new Date(0) : b.getEnqueueAt();
                int c = ea.compareTo(eb);
                if (c != 0) {
                    return c;
                }
                return Long.compare(a.getId() == null ? 0L : a.getId(),
                        b.getId() == null ? 0L : b.getId());
            }
        });
        return rows.get(0);
    }

    /** 同档同号只转一次：流感专档以交接留痕为凭（已转事项本身也进不了待转，这里是双保险） */
    private boolean alreadyTransferredInWindow(Long regId, String winCode) {
        if (winCode == null) {
            return false;
        }
        List<TMhRegHandover> done = this.mhRegHandoverMapper.selectList(
                new QueryWrapper<TMhRegHandover>()
                        .eq("reg_id", regId)
                        .eq("act_type", ACT_TRANSFER)
                        .eq("win_code", winCode)
                        .eq("del_flag", 0));
        return done != null && !done.isEmpty();
    }

    /**
     * 转成一笔：事项条件更新(CAS status 待转) + 候补条条件更新(CAS 在排) + 交接留痕，
     * 同一事务内同生共死；任一条件没抢上（被并发/重扫改过）整笔回滚，号仍算待转。
     */
    private boolean doTransfer(final TMhRegTask reg, final TMhWaitLine wait, final Date at,
                               final String winCode, final String roundNo) {
        final int[] taskUpdated = {0};
        final int[] waitUpdated = {0};
        Runnable body = new Runnable() {
            @Override
            public void run() {
                taskUpdated[0] = mhRegTaskMapper.update(null, new UpdateWrapper<TMhRegTask>()
                        .eq("id", reg.getId())
                        .eq("status", STATUS_WAIT)
                        .eq("del_flag", 0)
                        .set("status", STATUS_DONE)
                        .set("trans_at", at)
                        .set("update_time", at));
                if (taskUpdated[0] != 1) {
                    throw new IllegalStateException("事项已非待转，放弃转号");
                }
                waitUpdated[0] = mhWaitLineMapper.update(null, new UpdateWrapper<TMhWaitLine>()
                        .eq("id", wait.getId())
                        .eq("queue_status", QS_WAITING)
                        .eq("del_flag", 0)
                        .set("queue_status", QS_PICKED)
                        .set("picked_at", at)
                        .set("update_time", at));
                if (waitUpdated[0] != 1) {
                    throw new IllegalStateException("候补条已不在排，放弃转号");
                }
                writeHandover(reg, ACT_TRANSFER, winCode, wait, at, null, roundNo, at);
            }
        };
        executeAtomically(body);
        // 内存对象与库保持一致（不覆盖 release_at；trans_at 随本笔首次落定）
        reg.setStatus(STATUS_DONE);
        reg.setTransAt(at);
        reg.setUpdateTime(at);
        wait.setQueueStatus(QS_PICKED);
        wait.setPickedAt(at);
        return true;
    }

    /** 挂起一笔：状态置已挂起 + 原由 + 留痕同一事务；不写 trans_at，挂起的下轮不再被当待转翻出 */
    private boolean doSuspend(final TMhRegTask reg, final String reason, final Date at,
                              final String winCode, final String roundNo) {
        final int[] updated = {0};
        executeAtomically(new Runnable() {
            @Override
            public void run() {
                updated[0] = mhRegTaskMapper.update(null, new UpdateWrapper<TMhRegTask>()
                        .eq("id", reg.getId())
                        .eq("status", STATUS_WAIT)
                        .eq("del_flag", 0)
                        .set("status", STATUS_HOLD)
                        .set("suspend_reason", reason)
                        .set("update_time", at));
                if (updated[0] != 1) {
                    // 被并发改过状态：不留半条挂起痕
                    throw new IllegalStateException("事项已非待转，放弃挂起");
                }
                writeHandover(reg, ACT_SUSPEND, winCode, null, at, reason, roundNo, at);
            }
        });
        reg.setStatus(STATUS_HOLD);
        reg.setSuspendReason(reason);
        reg.setUpdateTime(at);
        return true;
    }

    /** 交接留痕只追加，谁接了/什么时候接的/什么原由都在这里 */
    private void writeHandover(TMhRegTask reg, int actType, String winCode, TMhWaitLine wait,
                               Date actAt, String reason, String roundNo, Date now) {
        TMhRegHandover h = new TMhRegHandover();
        h.setRegId(reg.getId());
        h.setItemNo(reg.getItemNo());
        h.setActType(actType);
        h.setWinCode(winCode);
        if (wait != null) {
            h.setFromWaitId(wait.getId());
            h.setCardId(wait.getCardId());
        }
        h.setActAt(actAt);
        h.setReason(reason);
        h.setRoundNo(roundNo);
        h.setDelFlag(0);
        h.setCreateTime(now == null ? actAt : now);
        h.setUpdateTime(now == null ? actAt : now);
        this.mhRegHandoverMapper.insert(h);
    }

    /** 有事务管理器走编程式事务（规避自调用代理失效）；裸装测试环境直跑，CAS 更新兜底原子性 */
    private void executeAtomically(Runnable body) {
        if (this.transactionManager == null) {
            body.run();
            return;
        }
        new TransactionTemplate(this.transactionManager).execute(status -> {
            body.run();
            return null;
        });
    }

    private static boolean safe(Integer delFlag) {
        return delFlag == null || delFlag.intValue() == 0;
    }

    private String newRoundNo(Date at, String winCode) {
        String stamp;
        synchronized (ROUND_SEQ) {
            stamp = (at == null) ? "00000000000000"
                    : new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(at);
            long seq = ROUND_SEQ.incrementAndGet();
            return "R" + stamp + "-" + (winCode == null ? "OUT" : winCode) + "-" + seq;
        }
    }

    /** 结账落库：三个数全部取自本轮内存点算，绝不再查库拼数（屏与库同一次点算） */
    private TMhScanRound insertRound(String roundNo, String winCode, int runStatus,
                                     int dueCount, int waitCount, int doneCount, int holdCount,
                                     String errMsg, Date at) {
        TMhScanRound round = new TMhScanRound();
        round.setRoundNo(roundNo);
        round.setWinCode(winCode);
        round.setScanAt(at);
        round.setRunStatus(runStatus);
        round.setDueCount(dueCount);
        round.setWaitCount(waitCount);
        round.setDoneCount(doneCount);
        round.setHoldCount(holdCount);
        round.setErrMsg(errMsg);
        round.setDelFlag(0);
        round.setCreateTime(at == null ? new Date() : at);
        round.setUpdateTime(at == null ? new Date() : at);
        try {
            this.mhScanRoundMapper.insert(round);
        } catch (RuntimeException ignore) {
            // 结账台写不进也不能影响三数返回；出错轮本身再失败只吞这一层
        }
        return round;
    }

    private ScanSettle finishIdle(String roundNo, String winCode, int dueCount, Date at) {
        insertRound(roundNo, winCode, ROUND_IDLE, dueCount, 0, 0, 0, null, at);
        return ScanSettle.idle(roundNo, winCode, dueCount);
    }

    private ScanSettle finishOk(String roundNo, String winCode, int dueCount,
                                int waitCount, int doneCount, int holdCount, Date at) {
        insertRound(roundNo, winCode, ROUND_OK, dueCount, waitCount, doneCount, holdCount, null, at);
        return ScanSettle.ok(roundNo, winCode, dueCount, waitCount, doneCount, holdCount);
    }

    private ScanSettle finishError(String roundNo, String winCode, String errMsg) {
        String msg = errMsg == null ? "未知错误" : errMsg;
        insertRound(roundNo, winCode, ROUND_ERR, 0, 0, 0, 0, msg, null);
        return ScanSettle.error(roundNo, winCode, msg);
    }
}
