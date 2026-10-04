package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMhHolidayMapper;
import com.fc.v2.mapper.auto.TMhRegClosureMapper;
import com.fc.v2.mapper.auto.TMhRegLogMapper;
import com.fc.v2.mapper.auto.TMhRegTaskMapper;
import com.fc.v2.mapper.auto.TMhRegWaitMapper;
import com.fc.v2.mapper.auto.TMhRegWindowMapper;
import com.fc.v2.mapper.custom.RegScanDao;
import com.fc.v2.model.auto.TMhHoliday;
import com.fc.v2.model.auto.TMhRegClosure;
import com.fc.v2.model.auto.TMhRegLog;
import com.fc.v2.model.auto.TMhRegTask;
import com.fc.v2.model.auto.TMhRegWait;
import com.fc.v2.model.auto.TMhRegWindow;
import com.fc.v2.model.custom.reg.RegRunResult;
import com.fc.v2.model.custom.reg.RegScanCounts;
import com.fc.v2.service.ITMhRegTaskService;

/**
 * 号源到期转号事项条 Service业务层处理（scheduling-job 形状：周期执行）。
 *
 * 业务口径（与值班约定逐条对齐）：
 * 1. 浮出：放号时挨个设到期日 due_at 与提前自然日数 amount，到 due_at-amount 当日浮出；
 * 2. 窗口：白天开一档（值班划的 t_mh_reg_window），夜间与法定节假日整段不扫，
 *    窗口外到期号原样放着等下轮；流感高峰单开一档（window_type=1），不走日常档；
 * 3. 候补次序：按挂队列时刻先后（enqueue_at 早的先），普通挂号（is_ordinary=1）不许插队，永不选取；
 *    号浮出后超期三天仍转不出去的，处理次序提到最前；
 * 4. 转成：状态 0待转→1已转 与 transferred_at 真转出时刻在同一条条件更新里落定，
 *    并写台账（谁接的、何时接的），三样同事务；少一样整笔回滚，下轮仍只认待转；
 * 5. 流感档：同一档里同一个号当天只转一次，当天二次进队列（又落回待转）直接忽略，先前那次原样保留；
 * 6. 挂起：转号前医生休诊 / 科室停摆 / 候补整队列撤了（t_mh_reg_closure），就地置 3挂起并写原由，
 *    挂起的不再参与后续转号；挂一个不拖整轮——每条号独立事务，单条失败只计 failed，其余照常；
 * 7. 安置：过了期限仍转不出去，只能撤号（软删+台账）或改用途；
 *    最早定的放号日 release_no/create_time 与真转出时刻 transferred_at 任何动作都不许覆写；
 * 8. 结账：待转/已转/挂起只算这一轮，三个数出自同一条 SQL 的同一次点算；
 *    窗口外（executed=false）、本轮无活（dueCount=0）、处理出错（error=true）三态明说，互不混淆。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMhRegTaskServiceImpl implements ITMhRegTaskService {

    /** 条目情形 */
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;
    private static final int STATUS_SUSPEND = 3;

    /** 排队情形 */
    private static final int WAIT_ON = 0;
    private static final int WAIT_TAKEN = 1;
    private static final int WAIT_CANCEL = 2;

    /** 窗口档别 */
    private static final int WIN_DAILY = 0;
    private static final int WIN_FLU = 1;

    /** 台账动作 */
    private static final int LOG_TRANSFER = 1;
    private static final int LOG_SUSPEND = 2;
    private static final int LOG_CANCEL = 3;
    private static final int LOG_REPURPOSE = 4;

    /** 停摆类别 */
    private static final int CLOSURE_DOCTOR = 1;
    private static final int CLOSURE_DEPT = 2;
    private static final int CLOSURE_QUEUE = 3;

    /** 浮出后超期几天提至处理最前 */
    private static final int OVERDONE_DAYS = 3;

    @Resource
    private TMhRegTaskMapper mhRegTaskMapper;
    @Resource
    private TMhRegWaitMapper mhRegWaitMapper;
    @Resource
    private TMhRegWindowMapper mhRegWindowMapper;
    @Resource
    private TMhHolidayMapper mhHolidayMapper;
    @Resource
    private TMhRegClosureMapper mhRegClosureMapper;
    @Resource
    private TMhRegLogMapper mhRegLogMapper;
    @Resource
    private RegScanDao regScanDao;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final SimpleDateFormat DAY_FMT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat SEC_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat HM_FMT = new SimpleDateFormat("HH:mm:ss");
    private static final SimpleDateFormat BATCH_FMT = new SimpleDateFormat("yyyyMMddHHmmssSSS");

    @Override
    public TMhRegTask selectTMhRegTaskById(Long id) {
        return this.mhRegTaskMapper.selectById(id);
    }

    /**
     * 时段比较沿用墙钟字符串口径（见分诊模块踩坑记录）：
     * JDBC serverTimezone 与本机不对称，直接拿 Date 作参数会整体偏移，"恰好到期"边界会错判。
     */
    private static String ts(Date d) {
        synchronized (SEC_FMT) {
            return SEC_FMT.format(d);
        }
    }

    // ============================ 到期浮出 ============================

    @Override
    public List<TMhRegTask> listDue(Date at) {
        if (at == null) {
            return new ArrayList<TMhRegTask>();
        }
        // 只捞待转号；已转/挂起/转不出去的绝不翻出来（"下一轮再扫不能当待转翻出来"）
        List<TMhRegTask> all = this.mhRegTaskMapper.selectList(new QueryWrapper<TMhRegTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT)
                .isNotNull("due_at"));
        List<TMhRegTask> due = new ArrayList<TMhRegTask>();
        for (TMhRegTask r : all) {
            Date surface = surfaceAt(r);
            if (surface != null && ts(surface).compareTo(ts(at)) <= 0) {
                due.add(r);
            }
        }
        // 确定性基础次序：浮出先后，同刻按 id；超期提头在 runOnce 内按扫号时刻再排
        Collections.sort(due, new Comparator<TMhRegTask>() {
            @Override
            public int compare(TMhRegTask a, TMhRegTask b) {
                int c = ts(surfaceAt(a)).compareTo(ts(surfaceAt(b)));
                return c != 0 ? c : a.getId().compareTo(b.getId());
            }
        });
        return due;
    }

    /** 浮出时刻 = 放号截止 - 提前自然日数（整天减，null/脏值按 0 天） */
    private static Date surfaceAt(TMhRegTask r) {
        if (r.getDueAt() == null) {
            return null;
        }
        int days = 0;
        if (r.getAmount() != null && r.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            days = r.getAmount().setScale(0, BigDecimal.ROUND_DOWN).intValue();
        }
        Calendar c = Calendar.getInstance();
        c.setTime(r.getDueAt());
        c.add(Calendar.DAY_OF_MONTH, -days);
        return c.getTime();
    }

    // ============================ 扫号主流程 ============================

    @Override
    public RegRunResult runOnce() {
        return runOnce(new Date());
    }

    @Override
    public RegRunResult runOnce(Date at) {
        if (at == null) {
            at = new Date();
        }
        final Date now = at;
        RegRunResult result = new RegRunResult();
        result.setAt(now);

        // ① 窗口判定：值班划的窗口 + 法定节假日整段不扫
        TMhRegWindow win = resolveWindow(now);
        if (win == null) {
            // 窗口外：到期号也放着不动，库里一个字节不写——这不是出错
            result.setExecuted(false);
            result.setMessage(isHoliday(now)
                    ? "法定节假日整段不扫，到期号留待下一窗口"
                    : "窗口外（夜间非值班时段）不扫，到期号留待下一窗口");
            return result;
        }
        result.setExecuted(true);
        result.setWindowCode(win.getWindowCode());
        result.setWindowType(win.getWindowType());
        String batchNo;
        synchronized (BATCH_FMT) {
            batchNo = "REG" + BATCH_FMT.format(now);
        }
        result.setBatchNo(batchNo);

        // ② 快照本轮到期号（同一次点到的集合，结账三数只认这份 id）
        List<TMhRegTask> due = listDue(now);
        List<Long> snapshotIds = new ArrayList<Long>();
        for (TMhRegTask r : due) {
            snapshotIds.add(r.getId());
        }
        result.setDueCount(due.size());
        if (due.isEmpty()) {
            // 明说本轮无活：executed=true 且无到期号，与执行出错是两回事
            fillCounts(result, snapshotIds, batchNo);
            result.setMessage("本轮无活：窗口内没有到期待转号");
            return result;
        }

        // ③ 超期三天提头的次序要按"扫号时刻"重排（listDue 的预排只兜底）
        Collections.sort(due, processOrderAt(now));

        // 转号前停摆每轮只查一次（同一轮内口径一致，也避免每号打一遍库）
        final List<TMhRegClosure> closures = this.mhRegClosureMapper.selectList(
                new QueryWrapper<TMhRegClosure>().eq("del_flag", 0).eq("status", 0));

        // ④ 逐条独立事务处理：挂一个不拖整轮
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        for (TMhRegTask r : due) {
            // 流感档当天二次进队列：同档同号当天已转过一次的，直接忽略、原样保留（事务外只读判定）
            if (win.getWindowType() != null && win.getWindowType() == WIN_FLU
                    && alreadyTransferredToday(r.getId(), now)) {
                result.setSkippedDuplicateCount(result.getSkippedDuplicateCount() + 1);
                continue;
            }
            try {
                Outcome oc = tx.execute(status -> processOne(r, win, batchNo, now, closures));
                if (oc == Outcome.TRANSFERRED) {
                    result.setTransferredCount(result.getTransferredCount() + 1);
                } else if (oc == Outcome.SUSPENDED) {
                    result.setSuspendedCount(result.getSuspendedCount() + 1);
                }
                // WAITING：没人接，留在待转，不计任何动作数（结账时库内点算为待转）
            } catch (RuntimeException ex) {
                // 单条失败：该条事务已回滚，号仍落待转，下轮窗口再动；不拖整轮
                result.setFailedCount(result.getFailedCount() + 1);
                result.setError(true);
            }
        }

        // ⑤ 同一次点算：屏上三数与库内实数同源（内存动作数仅作旁证，屏数以点算为准）
        fillCounts(result, snapshotIds, batchNo);
        if (result.isError()) {
            result.setMessage("本轮扫完，" + result.getTransferredCount() + " 条已转、"
                    + result.getSuspendedCount() + " 条挂起、" + result.getFailedCount()
                    + " 条处理失败留待下轮");
        } else {
            result.setMessage("本轮扫完，" + result.getTransferredCount() + " 条已转、"
                    + result.getSuspendedCount() + " 条挂起、" + result.getWaitingCount() + " 条仍待转");
        }
        return result;
    }

    private enum Outcome { TRANSFERRED, SUSPENDED, WAITING }

    /** 处理一条号（运行在该号自己的事务里，抛异常即整条回滚） */
    private Outcome processOne(TMhRegTask snapshot, TMhRegWindow win, String batchNo, Date now,
                               List<TMhRegClosure> closures) {
        Long id = snapshot.getId();
        // 行级再认：进事务后重查并锁行，只认仍落待转的（双实例/上一轮残留双保险）
        TMhRegTask cur = this.mhRegTaskMapper.selectOne(new QueryWrapper<TMhRegTask>()
                .eq("id", id).eq("del_flag", 0).eq("status", STATUS_WAIT).last("for update"));
        if (cur == null) {
            // 已不是待转（并发被别的窗口转走/挂起）——不算待转也不算动作
            return Outcome.WAITING;
        }

        // ① 转号前停摆：医生休诊 / 科室停摆 / 候补整队列撤了 → 就地挂起写原由
        String closureReason = closureReason(cur, now, closures);
        if (closureReason != null) {
            int n = this.mhRegTaskMapper.update(null, new UpdateWrapper<TMhRegTask>()
                    .set("status", STATUS_SUSPEND)
                    .set("suspend_reason", closureReason)
                    .set("update_time", now)
                    .eq("id", id).eq("del_flag", 0).eq("status", STATUS_WAIT));
            if (n != 1) {
                throw new IllegalStateException("挂起条件更新未命中唯一行: id=" + id + " rows=" + n);
            }
            writeLog(cur, null, win.getWindowCode(), batchNo, LOG_SUSPEND, closureReason, now);
            return Outcome.SUSPENDED;
        }

        // ② 取队头：挂队列时刻早的先；普通挂号永不入选（不许插队）。行锁防双窗口同人。
        TMhRegWait head = pickQueueHead(cur.getQueueKey(), now);
        if (head == null) {
            // 候补排空：号留待转，下轮窗口再动（不是挂起、不是失败）
            return Outcome.WAITING;
        }

        // ③ 先占候补人：条件更新只认"在排"，占不到说明被并发抢走
        int claimed = this.mhRegWaitMapper.update(null, new UpdateWrapper<TMhRegWait>()
                .set("status", WAIT_TAKEN)
                .set("update_time", now)
                .eq("id", head.getId()).eq("status", WAIT_ON));
        if (claimed != 1) {
            return Outcome.WAITING;
        }

        // ④ 号落已转 + 真转出时刻同一条条件更新（两样少一样都不算转成）；
        //    只认待转且未记过承接人/转出时刻的行，从根上挡住重复转。
        int moved;
        try {
            moved = this.mhRegTaskMapper.update(null, new UpdateWrapper<TMhRegTask>()
                    .set("status", STATUS_DONE)
                    .set("wait_id", head.getId())
                    .set("transferred_at", now)
                    .set("update_time", now)
                    .eq("id", id)
                    .eq("del_flag", 0)
                    .eq("status", STATUS_WAIT)
                    .isNull("wait_id")
                    .isNull("transferred_at"));
        } catch (RuntimeException ex) {
            // 号没落定：把刚占的候补人放回在排，保持候补不丢
            releaseWaiter(head.getId(), now);
            throw ex;
        }
        if (moved != 1) {
            releaseWaiter(head.getId(), now);
            return Outcome.WAITING;
        }

        // ⑤ 台账：谁接的、什么时候接的，与转号同事务落账
        writeLog(cur, head, win.getWindowCode(), batchNo, LOG_TRANSFER, null, now);
        return Outcome.TRANSFERRED;
    }

    private void releaseWaiter(Long waitId, Date now) {
        this.mhRegWaitMapper.update(null, new UpdateWrapper<TMhRegWait>()
                .set("status", WAIT_ON)
                .set("update_time", now)
                .eq("id", waitId).eq("status", WAIT_TAKEN));
    }

    // ============================ 停摆/挂起 ============================

    /** 查转号前停摆：医生休诊 / 科室停摆 / 候补整队列撤了；命中返回原由，否则 null */
    private String closureReason(TMhRegTask r, Date now, List<TMhRegClosure> list) {
        String nowStr = ts(now);
        for (TMhRegClosure c : list) {
            if (!coverAt(c, nowStr)) {
                continue;
            }
            Integer t = c.getClosureType();
            if (t == null) {
                continue;
            }
            if (t == CLOSURE_DOCTOR) {
                if (str(c.getDoctorNo()) != null && str(c.getDoctorNo()).equals(str(r.getDoctorNo()))) {
                    return "医生休诊：" + nz(c.getRemark(), c.getDoctorNo());
                }
            } else if (t == CLOSURE_DEPT) {
                if (str(c.getQueueKey()) != null && str(c.getQueueKey()).equals(str(r.getQueueKey()))) {
                    return "科室停摆：" + nz(c.getRemark(), c.getQueueKey());
                }
            } else if (t == CLOSURE_QUEUE) {
                if (str(c.getQueueKey()) != null && str(c.getQueueKey()).equals(str(r.getQueueKey()))) {
                    return "候补整队列撤了：" + nz(c.getRemark(), c.getQueueKey());
                }
            }
        }
        return null;
    }

    /** 停摆区间覆盖此刻：起含、止不含；止空=至今未恢复（只用日期串比较，避开时区坑） */
    private static boolean coverAt(TMhRegClosure c, String nowStr) {
        if (c.getEffStart() == null) {
            return false;
        }
        String s = ts(c.getEffStart());
        if (s.compareTo(nowStr) > 0) {
            return false;
        }
        if (c.getEffEnd() != null && ts(c.getEffEnd()).compareTo(nowStr) <= 0) {
            return false;
        }
        return true;
    }

    // ============================ 候补队列 ============================

    /**
     * 取队头：同一队列在排候补（is_ordinary=0 才许被选，普通挂号不许插队），
     * 按挂队列时刻先后；行锁锁住选中行。
     */
    private TMhRegWait pickQueueHead(String queueKey, Date now) {
        if (queueKey == null || queueKey.isEmpty()) {
            return null;
        }
        List<TMhRegWait> rows = this.mhRegWaitMapper.selectList(new QueryWrapper<TMhRegWait>()
                .eq("del_flag", 0)
                .eq("status", WAIT_ON)
                .eq("queue_key", queueKey)
                .and(w -> w.isNull("is_ordinary").or().ne("is_ordinary", 1))
                .orderByAsc("enqueue_at").orderByAsc("id")
                .last("for update"));
        return (rows == null || rows.isEmpty()) ? null : rows.get(0);
    }

    /** 流感档去重：同号当天已有转出台账（先前那次原样保留，二次进队列直接忽略） */
    private boolean alreadyTransferredToday(Long taskId, Date now) {
        if (taskId == null) {
            return false;
        }
        List<TMhRegLog> logs = this.mhRegLogMapper.selectList(new QueryWrapper<TMhRegLog>()
                .eq("del_flag", 0)
                .eq("action", LOG_TRANSFER)
                .eq("task_id", taskId));
        String today;
        synchronized (DAY_FMT) {
            today = DAY_FMT.format(now);
        }
        for (TMhRegLog l : logs) {
            if (l.getActedAt() != null && DAY_FMT.format(l.getActedAt()).equals(today)) {
                return true;
            }
        }
        return false;
    }

    // ============================ 撤号 / 改用途 ============================

    @Override
    public int cancelTask(Long id, String reason) {
        if (id == null) {
            return 0;
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Integer n = tx.execute(status -> {
            TMhRegTask cur = this.mhRegTaskMapper.selectById(id);
            if (cur == null || cur.getDelFlag() != null && cur.getDelFlag() == 1) {
                return 0;
            }
            // 已转出的号有人接了，不许撤；挂起的按停摆安置另议，也不在此撤
            if (cur.getStatus() != null
                    && (cur.getStatus() == STATUS_DONE || cur.getStatus() == STATUS_SUSPEND)) {
                return 0;
            }
            // 只软删留痕：绝不触碰 release_no / create_time（最早放号日）、transferred_at（真转出时刻）
            int rows = this.mhRegTaskMapper.update(null, new UpdateWrapper<TMhRegTask>()
                    .set("del_flag", 1)
                    .set("status", STATUS_FAIL)
                    .set("remark", clamp(nz(reason, "超期未转出撤号")))
                    .set("update_time", new Date())
                    .eq("id", id).eq("del_flag", 0)
                    .ne("status", STATUS_DONE));
            if (rows == 1) {
                writeLog(cur, null, null, null, LOG_CANCEL, clamp(nz(reason, "超期未转出撤号")), new Date());
            }
            return rows;
        });
        return n == null ? 0 : n;
    }

    @Override
    public int repurposeTask(Long id, String newContent, String reason) {
        if (id == null) {
            return 0;
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Integer n = tx.execute(status -> {
            TMhRegTask cur = this.mhRegTaskMapper.selectById(id);
            if (cur == null || (cur.getDelFlag() != null && cur.getDelFlag() == 1)) {
                return 0;
            }
            if (cur.getStatus() != null
                    && (cur.getStatus() == STATUS_DONE || cur.getStatus() == STATUS_SUSPEND)) {
                return 0;
            }
            // 改用途：落"转不出去"终态；只改事由记要，放号日/转出时刻列根本不出现在 SET 里
            int rows = this.mhRegTaskMapper.update(null, new UpdateWrapper<TMhRegTask>()
                    .set("status", STATUS_FAIL)
                    .set("content", clamp(newContent == null ? "" : newContent))
                    .set("remark", clamp(nz(reason, "超期未转出改用途")))
                    .set("update_time", new Date())
                    .eq("id", id).eq("del_flag", 0)
                    .ne("status", STATUS_DONE));
            if (rows == 1) {
                writeLog(cur, null, null, null, LOG_REPURPOSE,
                        clamp(nz(reason, "超期未转出改用途")), new Date());
            }
            return rows;
        });
        return n == null ? 0 : n;
    }

    // ============================ 台账 ============================

    private void writeLog(TMhRegTask task, TMhRegWait waiter, String windowCode,
                          String batchNo, int action, String reason, Date at) {
        TMhRegLog log = new TMhRegLog();
        log.setTaskId(task.getId());
        log.setItemNo(task.getItemNo());
        log.setReleaseNo(task.getReleaseNo());
        log.setQueueKey(task.getQueueKey());
        if (waiter != null) {
            log.setWaitId(waiter.getId());
            log.setWaiterNo(waiter.getWaiterNo());
        }
        log.setWindowCode(windowCode);
        log.setRunBatch(batchNo);
        log.setAction(action);
        log.setReason(reason);
        log.setActedAt(at);
        log.setDelFlag(0);
        log.setCreateBy("reg-scan");
        log.setCreateTime(at);
        this.mhRegLogMapper.insert(log);
    }

    // ============================ 结账点算 ============================

    /** 三个数同一条 SQL 一次点算（RegScanDao 内三个子选择同源同时刻） */
    private void fillCounts(RegRunResult result, List<Long> snapshotIds, String batchNo) {
        // 本轮没点到任何号：待转就是 0，绝不能退化成"全表待转数"
        if (snapshotIds == null || snapshotIds.isEmpty()) {
            result.setWaitingCount(0);
            result.setTransferredCount(0);
            result.setSuspendedCount(0);
            return;
        }
        RegScanCounts c = regScanDao.recountThisRun(snapshotIds, batchNo);
        if (c == null) {
            result.setWaitingCount(0);
            result.setTransferredCount(0);
            result.setSuspendedCount(0);
            return;
        }
        result.setWaitingCount(c.getWaitingCount() == null ? 0 : c.getWaitingCount());
        result.setTransferredCount(c.getTransferredCount() == null ? 0 : c.getTransferredCount());
        result.setSuspendedCount(c.getSuspendedCount() == null ? 0 : c.getSuspendedCount());
    }

    // ============================ 窗口判定 ============================

    private TMhRegWindow resolveWindow(Date at) {
        // 法定节假日整段不扫（夜间本身就没有窗口，节假日即使配了档也不扫）
        if (isHoliday(at)) {
            return null;
        }
        List<TMhRegWindow> wins = this.mhRegWindowMapper.selectList(new QueryWrapper<TMhRegWindow>()
                .eq("del_flag", 0).eq("status", 0));
        TMhRegWindow hit = null;
        for (TMhRegWindow w : wins) {
            if (!windowCovers(w, at)) {
                continue;
            }
            if (hit == null || windowPriority(w) > windowPriority(hit)) {
                hit = w;
            }
        }
        return hit;
    }

    /** 窗口覆盖此刻：档别有效期含当日 + 星期位为 1 + 时刻在[开,关]内；关窗时刻含 */
    private static boolean windowCovers(TMhRegWindow w, Date at) {
        String day;
        synchronized (DAY_FMT) {
            day = DAY_FMT.format(at);
        }
        if (w.getEffStart() != null) {
            if (DAY_FMT.format(w.getEffStart()).compareTo(day) > 0) {
                return false;
            }
        }
        if (w.getEffEnd() != null) {
            if (DAY_FMT.format(w.getEffEnd()).compareTo(day) <= 0) {
                return false;
            }
        }
        String mask = w.getWeekdayMask();
        if (mask != null && mask.length() >= 7) {
            Calendar c = Calendar.getInstance();
            c.setTime(at);
            int dow = c.get(Calendar.DAY_OF_WEEK); // 周日=1 ... 周六=7
            int idx = (dow + 5) % 7;               // 周一=0 ... 周日=6
            if (mask.charAt(idx) != '1') {
                return false;
            }
        }
        String t;
        synchronized (HM_FMT) {
            t = HM_FMT.format(at);
        }
        if (w.getOpenAt() != null && w.getOpenAt().compareTo(t) > 0) {
            return false;
        }
        if (w.getCloseAt() != null && w.getCloseAt().compareTo(t) < 0) {
            return false;
        }
        return true;
    }

    /** 流感高峰单开档不走日常档：撞期时流感档（type=1）说话，其次看 priority */
    private static int windowPriority(TMhRegWindow w) {
        int base = (w.getWindowType() != null && w.getWindowType() == WIN_FLU) ? 1000 : 0;
        return base + (w.getPriority() == null ? 0 : w.getPriority());
    }

    private boolean isHoliday(Date at) {
        String day;
        synchronized (DAY_FMT) {
            day = DAY_FMT.format(at);
        }
        List<TMhHoliday> hs = this.mhHolidayMapper.selectList(new QueryWrapper<TMhHoliday>()
                .eq("del_flag", 0).eq("status", 0));
        for (TMhHoliday h : hs) {
            if (h.getHolidayDate() != null && DAY_FMT.format(h.getHolidayDate()).equals(day)) {
                return true;
            }
        }
        return false;
    }

    // ============================ 杂项 ============================

    /** 按扫号时刻判定的超期提头排序（浮出+3天 <= at 提最前），再按浮出先后、id */
    private static Comparator<TMhRegTask> processOrderAt(final Date now) {
        return new Comparator<TMhRegTask>() {
            @Override
            public int compare(TMhRegTask a, TMhRegTask b) {
                Date sa = surfaceAt(a);
                Date sb = surfaceAt(b);
                boolean oa = plusDays(sa, OVERDONE_DAYS).compareTo(now) <= 0;
                boolean ob = plusDays(sb, OVERDONE_DAYS).compareTo(now) <= 0;
                if (oa != ob) {
                    return oa ? -1 : 1;
                }
                int c = ts(sa).compareTo(ts(sb));
                if (c != 0) {
                    return c;
                }
                return a.getId().compareTo(b.getId());
            }
        };
    }

    private static Date plusDays(Date d, int days) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTime();
    }

    private static String str(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private static String nz(String s, String fallback) {
        String v = str(s);
        return v != null ? v : fallback;
    }

    /** 文本统一截断，防超长撑坏台账 */
    private static String clamp(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 255 ? s.substring(0, 255) : s;
    }
}
