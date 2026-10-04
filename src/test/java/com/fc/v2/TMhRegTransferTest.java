package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TMhHolidayMapper;
import com.fc.v2.mapper.auto.TMhRegHandoverMapper;
import com.fc.v2.mapper.auto.TMhRegTaskMapper;
import com.fc.v2.mapper.auto.TMhScanRoundMapper;
import com.fc.v2.mapper.auto.TMhScanWindowMapper;
import com.fc.v2.mapper.auto.TMhWaitLineMapper;
import com.fc.v2.model.auto.TMhRegHandover;
import com.fc.v2.model.auto.TMhRegTask;
import com.fc.v2.model.auto.TMhScanRound;
import com.fc.v2.model.auto.TMhWaitLine;
import com.fc.v2.service.ITMhRegTaskService.ScanSettle;
import com.fc.v2.service.impl.TMhRegTaskServiceImpl;

/**
 * 转号机制真库单测：H2(MODE=MySQL) + MyBatis-Plus，跑真实 SQL（CAS 更新/条件查询/留痕落库）。
 */
public class TMhRegTransferTest {

    private static final String QWAIT = "0"; // 候补排队
    private static final String QNORM = "1"; // 普通挂号

    private DriverManagerDataSource ds;
    private JdbcTemplate jdbc;
    private TMhRegTaskMapper taskMapper;
    private TMhWaitLineMapper waitMapper;
    private TMhRegHandoverMapper handoverMapper;
    private TMhScanRoundMapper roundMapper;
    private TMhRegTaskServiceImpl svc;

    private static final SimpleDateFormat F = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat FD = new SimpleDateFormat("yyyy-MM-dd");

    private static Date t(String s) {
        try {
            return F.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------------------------------------------------------- setup

    @org.junit.jupiter.api.BeforeEach
    public void setUp() throws Exception {
        ds = new DriverManagerDataSource(
                "jdbc:h2:mem:mh" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection c = ds.getConnection()) {
            ScriptUtils.executeSqlScript(c,
                    new EncodedResource(new ClassPathResource("schema-mh-h2.sql"), "UTF-8"));
        }
        jdbc = new JdbcTemplate(ds);

        MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
        fb.setDataSource(ds);
        fb.setConfiguration(new MybatisConfiguration());
        fb.setGlobalConfig(new GlobalConfig());
        SqlSessionFactory factory = fb.getObject();
        // 无 @MapperScan 容器：把 BaseMapper 手工注册进 MP 的 MybatisMapperRegistry
        for (Class<?> m : new Class<?>[]{TMhRegTaskMapper.class, TMhWaitLineMapper.class,
                TMhScanWindowMapper.class, TMhHolidayMapper.class,
                TMhRegHandoverMapper.class, TMhScanRoundMapper.class}) {
            factory.getConfiguration().addMapper(m);
        }
        SqlSessionTemplate sql = new SqlSessionTemplate(factory);

        taskMapper = sql.getMapper(TMhRegTaskMapper.class);
        waitMapper = sql.getMapper(TMhWaitLineMapper.class);
        TMhScanWindowMapper windowMapper = sql.getMapper(TMhScanWindowMapper.class);
        TMhHolidayMapper holidayMapper = sql.getMapper(TMhHolidayMapper.class);
        handoverMapper = sql.getMapper(TMhRegHandoverMapper.class);
        roundMapper = sql.getMapper(TMhScanRoundMapper.class);

        svc = new TMhRegTaskServiceImpl();
        ReflectionTestUtils.setField(svc, "mhRegTaskMapper", taskMapper);
        ReflectionTestUtils.setField(svc, "mhWaitLineMapper", waitMapper);
        ReflectionTestUtils.setField(svc, "mhScanWindowMapper", windowMapper);
        ReflectionTestUtils.setField(svc, "mhHolidayMapper", holidayMapper);
        ReflectionTestUtils.setField(svc, "mhRegHandoverMapper", handoverMapper);
        ReflectionTestUtils.setField(svc, "mhScanRoundMapper", roundMapper);
        ReflectionTestUtils.setField(svc, "transactionManager",
                new DataSourceTransactionManager((DataSource) ds));

        seedWindows();
    }

    private void seedWindows() {
        // 白天日常档 08:00（含）–18:00（不含）；时分秒串比较，日期部分取固定基准即可
        jdbc.update("INSERT INTO t_mh_scan_window(id,win_code,win_name,win_kind,day_from,day_to,status,del_flag)"
                + " VALUES(0,'DAY','白天日常档',0,'1970-01-01 08:00:00','1970-01-01 18:00:00',0,0)");
        // 流感高峰专档：10/05–10/18，每天 09:00–11:00
        jdbc.update("INSERT INTO t_mh_scan_window(id,win_code,win_name,win_kind,day_from,day_to,date_from,date_to,status,del_flag)"
                + " VALUES(1,'FLU','流感高峰档',1,'1970-01-01 09:00:00','1970-01-01 11:00:00','2026-10-05','2026-10-18',0,0)");
        // 法定节假日：国庆 10/01
        jdbc.update("INSERT INTO t_mh_holiday(id,holi_date,holi_name,holi_year,del_flag)"
                + " VALUES(0,'2026-10-01','国庆节',2026,0)");
    }

    /** 造一条待转号。releaseAt 可选；amount=提前转号自然日数 */
    private long seedTask(long id, String itemNo, String dueAt, double amount, Integer status) {
        jdbc.update("INSERT INTO t_mh_reg_task(id,item_no,due_at,amount,status,del_flag,create_time)"
                + " VALUES(?,?,?,?,?,0,?)",
                id, itemNo, Timestamp.valueOf(dueAt), BigDecimal.valueOf(amount),
                status == null ? 0 : status, Timestamp.valueOf(dueAt));
        return id;
    }

    private TMhRegTask task(long id) {
        return taskMapper.selectById(id);
    }

    private List<TMhWaitLine> waits(long regId) {
        return waitMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TMhWaitLine>()
                .eq("reg_id", regId).orderByAsc("id"));
    }

    private List<TMhRegHandover> handovers(long regId) {
        return handoverMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TMhRegHandover>()
                .eq("reg_id", regId).orderByAsc("id"));
    }

    private TMhScanRound lastRound() {
        List<TMhScanRound> all = roundMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TMhScanRound>()
                        .orderByDesc("id"));
        return all.isEmpty() ? null : all.get(0);
    }

    // ---------------------------------------------------------------- 窗口

    @org.junit.jupiter.api.Test
    public void dayWindowOpen_transferAtomicallyAndNotRescanned() {
        seedTask(10, "REG10", "2026-10-20 10:00:00", 0, 0);
        svc.enqueue(10L, 1001L, 0, t("2026-10-19 09:00:00"));

        ScanSettle s = svc.runOnce(t("2026-10-20 10:00:00"));

        assertEquals(1, s.getRunStatus());
        assertEquals(1, s.getDueCount());
        assertEquals(0, s.getWaitCount());
        assertEquals(1, s.getDoneCount());
        assertEquals(0, s.getHoldCount());
        assertEquals("FLU".equals(s.getWinCode()) ? "FLU" : "DAY", s.getWinCode());
        // 10/20 不在流感期，窗口必须是白天档
        assertEquals("DAY", s.getWinCode());

        TMhRegTask r = task(10);
        assertEquals(1, r.getStatus().intValue());           // 待转→已转
        assertNotNull(r.getTransAt());                       // 转出时刻同生
        assertEquals(t("2026-10-20 10:00:00"), r.getTransAt());

        TMhWaitLine w = waits(10).get(0);
        assertEquals(1, w.getQueueStatus().intValue());      // 已接号
        assertNotNull(w.getPickedAt());

        List<TMhRegHandover> hs = handovers(10);
        assertEquals(1, hs.size());
        assertEquals(1, hs.get(0).getActType().intValue());  // 转成留痕
        assertEquals(Long.valueOf(1001L), hs.get(0).getCardId());

        // 下一轮再扫：已转的不得当待转翻出
        ScanSettle again = svc.runOnce(t("2026-10-20 10:30:00"));
        assertEquals(0, again.getDueCount());
        assertEquals(0, again.getDoneCount());
        assertEquals("本轮无活", again.message());
    }

    @org.junit.jupiter.api.Test
    public void nightWindowClosed_dueNumbersJustWait() {
        seedTask(11, "REG11", "2026-10-20 10:00:00", 0, 0);
        svc.enqueue(11L, 1002L, 0, t("2026-10-19 09:00:00"));

        ScanSettle s = svc.runOnce(t("2026-10-20 19:30:00"));

        assertEquals(0, s.getRunStatus());
        assertNull(s.getWinCode());                          // 窗口外
        assertEquals(0, s.getDueCount());
        assertEquals(0, s.getDoneCount());
        assertEquals(0, task(11).getStatus().intValue());    // 号原样待转
        assertEquals("窗口外不扫，本轮无活", s.message());
    }

    @org.junit.jupiter.api.Test
    public void legalHolidayWholeDayNoScan() {
        seedTask(12, "REG12", "2026-10-01 09:00:00", 0, 0);
        svc.enqueue(12L, 1003L, 0, t("2026-09-30 09:00:00"));

        ScanSettle s = svc.runOnce(t("2026-10-01 10:00:00"));

        assertEquals(0, s.getRunStatus());
        assertNull(s.getWinCode());
        assertEquals(0, s.getDoneCount());
        assertEquals(0, task(12).getStatus().intValue());
    }

    // ---------------------------------------------------------------- 浮出

    @org.junit.jupiter.api.Test
    public void surfaceDayIsDueMinusAmount_inclusiveBoundary() {
        // due 10/20，提前 2 天 → 10/18 浮出（含当日）
        seedTask(13, "REG13", "2026-10-20 10:00:00", 2, 0);

        assertEquals(0, svc.listDue(t("2026-10-17 23:59:59")).size());
        assertEquals(1, svc.listDue(t("2026-10-18 00:00:00")).size());
    }

    // ---------------------------------------------------------------- 排队次序

    @org.junit.jupiter.api.Test
    public void normalRegistrationCannotJumpQueue() {
        seedTask(20, "REG20", "2026-10-20 10:00:00", 0, 0);
        // 普通挂号挂得早
        svc.enqueue(20L, 2001L, 1, t("2026-10-19 08:00:00"));
        // 候补排队挂得晚，仍该先转
        svc.enqueue(20L, 2002L, 0, t("2026-10-19 09:30:00"));

        svc.runOnce(t("2026-10-20 10:00:00"));

        TMhRegHandover h = handovers(20).get(0);
        assertEquals(Long.valueOf(2002L), h.getCardId());
        // 普通挂号那条仍在排
        TMhWaitLine normal = waits(20).get(0);
        assertEquals(0, normal.getQueueStatus().intValue());
    }

    @org.junit.jupiter.api.Test
    public void earlierEnqueueTransfersFirst() {
        seedTask(21, "REG21", "2026-10-20 10:00:00", 0, 0);
        svc.enqueue(21L, 2101L, 0, t("2026-10-19 09:30:00"));
        svc.enqueue(21L, 2102L, 0, t("2026-10-19 09:00:00"));

        svc.runOnce(t("2026-10-20 10:00:00"));

        assertEquals(Long.valueOf(2102L), handovers(21).get(0).getCardId());
    }

    @org.junit.jupiter.api.Test
    public void overdueThreeDaysMovesTaskToFront() {
        // 两条都到期：30 早到期（10-10 + 3天 <= 10-20 → 置顶），31 当天到期
        seedTask(30, "REG30", "2026-10-10 10:00:00", 0, 0);
        seedTask(31, "REG31", "2026-10-20 10:00:00", 0, 0);
        svc.enqueue(30L, 3001L, 0, t("2026-10-09 09:00:00"));
        svc.enqueue(31L, 3101L, 0, t("2026-10-19 09:00:00"));

        ScanSettle s = svc.runOnce(t("2026-10-20 10:00:00"));
        assertEquals(2, s.getDoneCount());

        List<TMhRegHandover> all = handoverMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TMhRegHandover>()
                        .eq("act_type", 1).orderByAsc("id"));
        assertEquals(Long.valueOf(30L), all.get(0).getRegId());
    }

    @org.junit.jupiter.api.Test
    public void overdueByNaturalDay_promotedEvenBeforeDueClockTime() {
        // 截止 10/17 23:00，10/20 10:00 点算：按自然日已满三天（17→20），虽未到 23:00 仍置顶
        seedTask(32, "REG32", "2026-10-17 23:00:00", 0, 0);
        seedTask(33, "REG33", "2026-10-20 09:00:00", 0, 0);
        svc.enqueue(32L, 3201L, 0, t("2026-10-16 09:00:00"));
        svc.enqueue(33L, 3301L, 0, t("2026-10-19 09:00:00"));

        ScanSettle s = svc.runOnce(t("2026-10-20 10:00:00"));
        assertEquals(2, s.getDoneCount());
        List<TMhRegHandover> all = handoverMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TMhRegHandover>()
                        .eq("act_type", 1).orderByAsc("id"));
        assertEquals(Long.valueOf(32L), all.get(0).getRegId());
    }

    @org.junit.jupiter.api.Test
    public void emptyQueueLeavesTaskWaiting() {        seedTask(22, "REG22", "2026-10-20 10:00:00", 0, 0);

        ScanSettle s = svc.runOnce(t("2026-10-20 10:00:00"));
        assertEquals(1, s.getWaitCount());
        assertEquals(0, s.getDoneCount());
        assertEquals(0, task(22).getStatus().intValue());
        assertNull(task(22).getTransAt());
    }

    // ---------------------------------------------------------------- 流感专档

    @org.junit.jupiter.api.Test
    public void fluPeriod_runsSpecialWindowNotDayWindow() {
        seedTask(40, "REG40", "2026-10-06 09:30:00", 0, 0);
        svc.enqueue(40L, 4001L, 0, t("2026-10-05 08:00:00"));

        // 专档时刻内：自动点算走 FLU
        ScanSettle s = svc.runOnce(t("2026-10-06 10:00:00"));
        assertEquals("FLU", s.getWinCode());
        assertEquals(1, s.getDoneCount());
        assertEquals("FLU", handovers(40).get(0).getWinCode());
    }

    @org.junit.jupiter.api.Test
    public void fluPeriod_dayWindowSuppressedEvenAtDayHours() {
        seedTask(41, "REG41", "2026-10-06 09:30:00", 0, 0);
        svc.enqueue(41L, 4101L, 0, t("2026-10-05 08:00:00"));

        // 08:30 落在白天档钟点、但流感期内白天档不开
        ScanSettle auto = svc.runOnce(t("2026-10-06 08:30:00"));
        assertNull(auto.getWinCode());
        assertEquals(0, task(41).getStatus().intValue());

        // 显式点日常档同样不开
        ScanSettle explicit = svc.runOnce(t("2026-10-06 10:00:00"), "DAY");
        assertNull(explicit.getWinCode());
        assertEquals(0, task(41).getStatus().intValue());

        // 专档夜间时刻也不开
        ScanSettle night = svc.runOnce(t("2026-10-06 12:00:00"), "FLU");
        assertNull(night.getWinCode());
    }

    @org.junit.jupiter.api.Test
    public void fluWindow_sameNumberTransfersOnlyOnce() {
        seedTask(42, "REG42", "2026-10-06 09:30:00", 0, 0);
        svc.enqueue(42L, 4201L, 0, t("2026-10-05 08:00:00"));

        ScanSettle first = svc.runOnce(t("2026-10-06 10:00:00"), "FLU");
        assertEquals(1, first.getDoneCount());

        // 当天同人二次进队列：直接忽略，先前那次（已接号）原样保留
        TMhWaitLine again = svc.enqueue(42L, 4201L, 0, t("2026-10-06 10:30:00"));
        assertNull(again);
        assertEquals(1, waits(42).size());
        assertEquals(1, waits(42).get(0).getQueueStatus().intValue());

        // 模拟脏态：号被改回待转，并补排了新人——同档已转过一次，本轮不得再转
        jdbc.update("UPDATE t_mh_reg_task SET status=0,trans_at=NULL WHERE id=42");
        jdbc.update("INSERT INTO t_mh_wait_line(id,reg_id,card_id,queue_type,enqueue_at,join_day,queue_status,del_flag)"
                + " VALUES(942,42,4299,0,'2026-10-06 10:31:00','2026-10-06',0,0)");

        ScanSettle second = svc.runOnce(t("2026-10-06 10:35:00"), "FLU");
        assertEquals(0, second.getDoneCount());
        assertEquals(1, second.getWaitCount());
        assertEquals(0, task(42).getStatus().intValue());
        List<TMhRegHandover> hs = handovers(42);
        assertEquals(1, hs.size());                            // 先前那次原样保留
        assertEquals(Long.valueOf(4201L), hs.get(0).getCardId());
    }

    @org.junit.jupiter.api.Test
    public void sameDaySecondEnqueueIgnored_earlierOneKept() {
        seedTask(43, "REG43", "2026-10-20 10:00:00", 0, 0);
        assertNotNull(svc.enqueue(43L, 4301L, 0, t("2026-10-19 09:00:00")));
        assertNull(svc.enqueue(43L, 4301L, 0, t("2026-10-19 15:00:00")));

        List<TMhWaitLine> ws = waits(43);
        assertEquals(1, ws.size());
        assertEquals(t("2026-10-19 09:00:00"), ws.get(0).getEnqueueAt());
    }

    // ---------------------------------------------------------------- 挂起

    @org.junit.jupiter.api.Test
    public void withdrawnWholeQueue_suspendsWithReasonAndDoesNotBlockRound() {
        seedTask(50, "REG50", "2026-10-20 10:00:00", 0, 0);
        seedTask(51, "REG51", "2026-10-20 10:00:00", 0, 0);
        svc.enqueue(50L, 5001L, 0, t("2026-10-19 09:00:00"));
        svc.enqueue(51L, 5101L, 0, t("2026-10-19 09:00:00"));

        assertTrue(svc.withdrawQueue(50L, "候补整队列撤了"));

        ScanSettle s = svc.runOnce(t("2026-10-20 10:00:00"));
        assertEquals(1, s.getDoneCount());                     // 挂一个不拖整轮
        assertEquals(1, s.getHoldCount());

        TMhRegTask held = task(50);
        assertEquals(3, held.getStatus().intValue());
        assertTrue(held.getSuspendReason().contains("撤了"));
        assertNull(held.getTransAt());
        assertEquals(2, handovers(50).get(0).getActType().intValue());

        // 挂起的下轮不再被当待转翻出
        ScanSettle next = svc.runOnce(t("2026-10-20 10:30:00"));
        assertEquals(0, next.getHoldCount());
        assertEquals(0, next.getDueCount());
    }

    @org.junit.jupiter.api.Test
    public void doctorLeave_suspendBeforeScanWritesReason() {
        seedTask(52, "REG52", "2026-10-20 10:00:00", 0, 0);
        svc.enqueue(52L, 5201L, 0, t("2026-10-19 09:00:00"));

        TMhRegTask held = svc.suspend(52L, "医生休诊", t("2026-10-20 08:30:00"));
        assertEquals(3, held.getStatus().intValue());
        assertEquals("医生休诊", held.getSuspendReason());

        ScanSettle s = svc.runOnce(t("2026-10-20 10:00:00"));
        assertEquals(0, s.getHoldCount());                     // 已挂起的不再处理
        assertEquals(0, s.getDueCount());

        // 无原由不许挂起；已转终态不许挂起
        assertNull(svc.suspend(52L, "  ", t("2026-10-20 10:00:00")));
    }

    // ---------------------------------------------------------------- 撤号/改用途

    @org.junit.jupiter.api.Test
    public void cancelKeepsTracesAndNeverOverwritesReleaseOrTransAt() {
        seedTask(60, "REG60", "2026-10-20 10:00:00", 0, 0);
        Date release = t("2026-10-15 08:00:00");
        svc.markRelease(60L, release);
        // 二次落放号日：不覆盖
        svc.markRelease(60L, t("2026-10-16 08:00:00"));
        assertEquals(release, task(60).getReleaseAt());

        TMhRegTask cancelled = svc.cancel(60L, "过了期限转不出去，撤号", t("2026-10-21 09:00:00"));
        assertEquals(4, cancelled.getStatus().intValue());
        assertEquals(release, task(60).getReleaseAt());       // 最早放号日未被覆盖
        assertNull(task(60).getTransAt());                    // 没转过就是没有
        TMhRegHandover h = handovers(60).get(0);
        assertEquals(3, h.getActType().intValue());
        assertTrue(h.getReason().contains("撤号"));

        // 已撤不得再撤/改用途
        assertNull(svc.cancel(60L, "再撤", t("2026-10-22 09:00:00")));
        assertNull(svc.repurpose(60L, "改用途", t("2026-10-22 09:00:00")));
    }

    @org.junit.jupiter.api.Test
    public void repurposeDoesNotTouchTransAtAfterTransfer() {
        seedTask(61, "REG61", "2026-10-20 10:00:00", 0, 0);
        svc.enqueue(61L, 6101L, 0, t("2026-10-19 09:00:00"));
        svc.markRelease(61L, t("2026-10-15 08:00:00"));
        svc.runOnce(t("2026-10-20 10:00:00"));
        Date transAt = task(61).getTransAt();
        Date releaseAt = task(61).getReleaseAt();
        assertNotNull(transAt);

        // 已转终态不许撤号/改用途，两个时刻都原样保留
        assertNull(svc.cancel(61L, "撤", t("2026-10-21 09:00:00")));
        assertNull(svc.repurpose(61L, "改用途", t("2026-10-21 09:00:00")));
        assertEquals(transAt, task(61).getTransAt());
        assertEquals(releaseAt, task(61).getReleaseAt());
        assertEquals(1, task(61).getStatus().intValue());
    }

    // ---------------------------------------------------------------- 结账 / 无活 vs 出错

    @org.junit.jupiter.api.Test
    public void settleNumbersOnScreenAndTableFromSameCount() {
        seedTask(70, "REG70", "2026-10-20 10:00:00", 0, 0);
        seedTask(71, "REG71", "2026-10-20 10:00:00", 0, 0);
        seedTask(72, "REG72", "2026-10-20 10:00:00", 0, 0);
        svc.enqueue(70L, 7001L, 0, t("2026-10-19 09:00:00"));   // 转得掉
        svc.enqueue(72L, 7201L, 0, t("2026-10-19 09:00:00"));
        svc.withdrawQueue(72L, "队列撤了");                       // 挂起
        // 71 无候补：待转

        ScanSettle s = svc.runOnce(t("2026-10-20 10:00:00"));

        TMhScanRound r = lastRound();
        assertEquals(s.getRoundNo(), r.getRoundNo());
        // 屏上三个数与库里同一轮次一行出自同一次点算
        assertEquals(s.getWaitCount(), r.getWaitCount().intValue());
        assertEquals(s.getDoneCount(), r.getDoneCount().intValue());
        assertEquals(s.getHoldCount(), r.getHoldCount().intValue());
        assertEquals(1, r.getRunStatus().intValue());
        assertEquals(1, r.getWaitCount().intValue());
        assertEquals(1, r.getDoneCount().intValue());
        assertEquals(1, r.getHoldCount().intValue());
        assertEquals(3, r.getDueCount().intValue());
    }

    @org.junit.jupiter.api.Test
    public void noDueIsIdle_notError() {
        ScanSettle s = svc.runOnce(t("2026-10-20 10:00:00"));
        assertEquals(0, s.getRunStatus());
        assertEquals(0, s.getDueCount());
        assertEquals("DAY", s.getWinCode());
        assertEquals("本轮无活", s.message());

        TMhScanRound r = lastRound();
        assertEquals(0, r.getRunStatus().intValue());
        assertNull(r.getErrMsg());
    }

    @org.junit.jupiter.api.Test
    public void missingScanTimeIsError_notIdle() {
        ScanSettle s = svc.runOnce(null);
        assertEquals(2, s.getRunStatus());
        assertTrue(s.message().contains("出错"));
        TMhScanRound r = lastRound();
        assertEquals(2, r.getRunStatus().intValue());
        assertNotNull(r.getErrMsg());
        assertFalse("本轮无活".equals(s.message()));
    }
}
