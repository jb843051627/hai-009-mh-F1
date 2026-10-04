package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.model.auto.TMhRegClosure;
import com.fc.v2.model.auto.TMhRegLog;
import com.fc.v2.model.auto.TMhRegTask;
import com.fc.v2.model.auto.TMhRegWait;
import com.fc.v2.model.auto.TMhRegWindow;
import com.fc.v2.model.auto.TMhHoliday;
import com.fc.v2.model.custom.reg.RegRunResult;
import com.fc.v2.service.impl.TMhRegTaskServiceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMhHolidayMapper;
import com.fc.v2.mapper.auto.TMhRegClosureMapper;
import com.fc.v2.mapper.auto.TMhRegLogMapper;
import com.fc.v2.mapper.auto.TMhRegTaskMapper;
import com.fc.v2.mapper.auto.TMhRegWaitMapper;
import com.fc.v2.mapper.auto.TMhRegWindowMapper;

/**
 * 转号机制全规则测试（H2 MySQL 模式 + 真 SQL，不起 Web/Shiro/Redis 容器）。
 *
 * @author fuce
 * @date 2026-10-04
 */
@SpringJUnitConfig(classes = RegServiceTest.Config.class)
class RegServiceTest {

    @Configuration
    @MapperScan(basePackages = {"com.fc.v2.mapper.auto", "com.fc.v2.mapper.custom"})
    static class Config {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:regtest;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
            ds.setUsername("sa");
            return ds;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
            fb.setDataSource(ds);
            MybatisConfiguration cfg = new MybatisConfiguration();
            cfg.setMapUnderscoreToCamelCase(true);
            cfg.setJdbcTypeForNull(JdbcType.NULL);
            fb.setConfiguration(cfg);
            GlobalConfig gc = new GlobalConfig();
            gc.setMetaObjectHandler(new com.baomidou.mybatisplus.core.handlers.MetaObjectHandler() {
                @Override
                public void insertFill(org.apache.ibatis.reflection.MetaObject mo) {
                    strictInsertFill(mo, "createBy", String.class, "test");
                    strictInsertFill(mo, "createTime", Date.class, new Date());
                }

                @Override
                public void updateFill(org.apache.ibatis.reflection.MetaObject mo) {
                    strictUpdateFill(mo, "updateBy", String.class, "test");
                    strictUpdateFill(mo, "updateTime", Date.class, new Date());
                }
            });
            fb.setGlobalConfig(gc);
            return fb.getObject();
        }

        @Bean
        ITMhRegTaskService regTaskService() {
            return new TMhRegTaskServiceImpl();
        }
    }

    @Autowired private DataSource ds;
    @Autowired private ITMhRegTaskService svc;
    @Autowired private TMhRegTaskMapper taskMapper;
    @Autowired private TMhRegWaitMapper waitMapper;
    @Autowired private TMhRegWindowMapper windowMapper;
    @Autowired private TMhHolidayMapper holidayMapper;
    @Autowired private TMhRegClosureMapper closureMapper;
    @Autowired private TMhRegLogMapper logMapper;

    private static final SimpleDateFormat DT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat D = new SimpleDateFormat("yyyy-MM-dd");

    private static Date dt(String s) { try { return DT.parse(s); } catch (Exception e) { throw new RuntimeException(e); } }
    private static Date d(String s) { try { return D.parse(s); } catch (Exception e) { throw new RuntimeException(e); } }

    /** 扫描时刻：2026-10-08（周四）10:00，落在白天档 08:00-18:00 */
    private static final String SCAN = "2026-10-08 10:00:00";
    private static final String SCAN_NIGHT = "2026-10-08 20:00:00";

    @BeforeEach
    void init() throws Exception {
        ScriptUtils.executeSqlScript(ds.getConnection(), new ClassPathResource("sql/reg-h2.sql"));
        // 默认白天档：每天可扫，08:00-18:00（星期位在夜间测试里另配）
        TMhRegWindow day = new TMhRegWindow();
        day.setId(0L);
        day.setWindowCode("DAY");
        day.setWindowName("白天日常档");
        day.setWindowType(0);
        day.setWeekdayMask("1111111");
        day.setOpenAt("08:00:00");
        day.setCloseAt("18:00:00");
        day.setPriority(0);
        day.setStatus(0);
        day.setDelFlag(0);
        windowMapper.insert(day);
    }

    // ---------- 造数小工具 ----------

    private TMhRegTask task(long id, String queue, String doctor, String due, String amount) {
        TMhRegTask t = new TMhRegTask();
        t.setId(id);
        t.setItemNo("IT" + id);
        t.setQueueKey(queue);
        t.setDoctorNo(doctor);
        t.setReleaseNo("REL" + id);
        t.setDueAt(due == null ? null : dt(due));
        t.setAmount(amount == null ? null : new java.math.BigDecimal(amount));
        t.setStatus(0);
        t.setDelFlag(0);
        t.setContent("专家号");
        t.setCreateTime(dt("2026-09-01 09:00:00"));
        taskMapper.insert(t);
        return t;
    }

    private TMhRegWait waiter(long id, String queue, String enqueueAt, int ordinary) {
        TMhRegWait w = new TMhRegWait();
        w.setId(id);
        w.setQueueKey(queue);
        w.setWaiterNo("W" + id);
        w.setWaiterName("候补" + id);
        w.setEnqueueAt(dt(enqueueAt));
        w.setIsOrdinary(ordinary);
        w.setStatus(0);
        w.setDelFlag(0);
        waitMapper.insert(w);
        return w;
    }

    private TMhHoliday holiday(long id, String day) {
        TMhHoliday h = new TMhHoliday();
        h.setId(id);
        h.setHolidayDate(d(day));
        h.setHolidayName("测试节");
        h.setStatus(0);
        h.setDelFlag(0);
        holidayMapper.insert(h);
        return h;
    }

    private TMhRegClosure closure(long id, int type, String doctor, String queue, String start, String end) {
        TMhRegClosure c = new TMhRegClosure();
        c.setId(id);
        c.setClosureType(type);
        c.setDoctorNo(doctor);
        c.setQueueKey(queue);
        c.setEffStart(dt(start));
        c.setEffEnd(end == null ? null : dt(end));
        c.setStatus(0);
        c.setDelFlag(0);
        c.setRemark("停摆" + id);
        closureMapper.insert(c);
        return c;
    }

    private long logCount(int action) {
        return logMapper.selectCount(new QueryWrapper<TMhRegLog>().eq("del_flag", 0).eq("action", action));
    }

    // ============================ 用例 ============================

    /** 本轮无活：窗口内但没有到期号 —— 明说无活，不是出错，且库里一个字不写 */
    @Test
    void noDue_isIdle_notError() {
        // due 明天此刻、提前 0 天 → 扫描时还没浮出
        task(1, "Q", null, "2026-10-09 10:00:00", "0");
        waiter(10, "Q", "2026-10-01 08:00:00", 0);

        RegRunResult r = svc.runOnce(dt(SCAN));

        assertTrue(r.isExecuted());
        assertEquals(0, r.getDueCount());
        assertEquals(0, r.getTransferredCount());
        assertEquals(0, r.getWaitingCount());
        assertEquals(0, r.getSuspendedCount());
        assertFalse(r.isError());
        assertTrue(r.getMessage().contains("本轮无活"));
        assertEquals(Integer.valueOf(0), taskMapper.selectById(1L).getStatus());
        assertEquals(0, logMapper.selectCount(new QueryWrapper<TMhRegLog>()));
    }

    /** 窗口外（夜间）：到期也放着，executed=false，状态/台账一字不动 —— 不是报错 */
    @Test
    void outsideWindow_night_nothingMoves() {
        task(1, "Q", null, "2026-10-08 09:00:00", "0");
        waiter(10, "Q", "2026-10-01 08:00:00", 0);

        RegRunResult r = svc.runOnce(dt(SCAN_NIGHT));

        assertFalse(r.isExecuted());
        assertFalse(r.isError());
        assertEquals(Integer.valueOf(0), taskMapper.selectById(1L).getStatus());
        assertEquals(0, logMapper.selectCount(new QueryWrapper<TMhRegLog>()));
        assertNull(r.getWindowCode());
    }

    /** 法定节假日整段不扫 */
    @Test
    void holiday_wholeDaySkipped() {
        task(1, "Q", null, "2026-10-08 09:00:00", "0");
        waiter(10, "Q", "2026-10-01 08:00:00", 0);
        holiday(99, "2026-10-08");

        RegRunResult r = svc.runOnce(dt(SCAN));

        assertFalse(r.isExecuted());
        assertTrue(r.getMessage().contains("法定节假日"));
        assertEquals(Integer.valueOf(0), taskMapper.selectById(1L).getStatus());
        assertEquals(0, logMapper.selectCount(new QueryWrapper<TMhRegLog>()));
    }

    /** 正常转出：队头承接，状态/承接人/转出时刻同落，台账写清谁接的、何时接的 */
    @Test
    void transfer_fifoHead_atomicFieldsAndLog() {
        task(1, "Q1", null, "2026-10-08 09:00:00", "0");
        waiter(10, "Q1", "2026-10-01 08:00:00", 0);
        waiter(11, "Q1", "2026-10-02 08:00:00", 0);

        RegRunResult r = svc.runOnce(dt(SCAN));

        assertEquals(1, r.getDueCount());
        assertEquals(1, r.getTransferredCount());
        assertEquals(0, r.getWaitingCount());
        assertEquals(0, r.getSuspendedCount());
        TMhRegTask t = taskMapper.selectById(1L);
        assertEquals(Integer.valueOf(1), t.getStatus());
        assertEquals(Long.valueOf(10L), t.getWaitId());          // 挂得早的先接
        assertEquals(dt(SCAN), t.getTransferredAt());           // 转出时刻与状态同落
        assertEquals(Integer.valueOf(1), waitMapper.selectById(10L).getStatus());
        assertEquals(Integer.valueOf(0), waitMapper.selectById(11L).getStatus());
        TMhRegLog log = logMapper.selectOne(new QueryWrapper<TMhRegLog>().eq("task_id", 1L));
        assertEquals(Integer.valueOf(1), log.getAction());
        assertEquals("W10", log.getWaiterNo());
        assertEquals(Long.valueOf(10L), log.getWaitId());
        assertEquals(dt(SCAN), log.getActedAt());
        assertEquals(r.getBatchNo(), log.getRunBatch());
        assertEquals("DAY", log.getWindowCode());
    }

    /** 普通挂号不许插队：即便挂得更早也只能候补的人接 */
    @Test
    void ordinaryRegistration_neverJumpsQueue() {
        task(1, "Q1", null, "2026-10-08 09:00:00", "0");
        waiter(10, "Q1", "2026-09-30 08:00:00", 1); // 普通挂号，最早
        waiter(11, "Q1", "2026-10-02 08:00:00", 0); // 候补

        RegRunResult r = svc.runOnce(dt(SCAN));

        assertEquals(1, r.getTransferredCount());
        assertEquals(Long.valueOf(11L), taskMapper.selectById(1L).getWaitId());
        assertEquals(Integer.valueOf(0), waitMapper.selectById(10L).getStatus());
    }

    /** 候补排空：号留待转；下一轮窗口有人入队后正常转出，同号只转一次 */
    @Test
    void emptyQueue_waitsThenTransfersNextRun() {
        task(1, "Q1", null, "2026-10-08 09:00:00", "0");

        RegRunResult r1 = svc.runOnce(dt(SCAN));
        assertEquals(1, r1.getDueCount());
        assertEquals(1, r1.getWaitingCount());
        assertEquals(0, r1.getTransferredCount());
        assertEquals(Integer.valueOf(0), taskMapper.selectById(1L).getStatus());
        assertEquals(0, logCount(1));

        waiter(10, "Q1", "2026-10-08 10:30:00", 0);
        RegRunResult r2 = svc.runOnce(dt("2026-10-08 14:00:00"));
        assertEquals(1, r2.getTransferredCount());
        assertEquals(Integer.valueOf(1), taskMapper.selectById(1L).getStatus());
        assertEquals(Long.valueOf(10L), taskMapper.selectById(1L).getWaitId());
        assertEquals(1, logCount(1)); // 没有重复台账
    }

    /** 已转的下轮再扫绝不当待转翻出来（幂等） */
    @Test
    void transferred_notPickedAgain() {
        task(1, "Q1", null, "2026-10-08 09:00:00", "0");
        waiter(10, "Q1", "2026-10-01 08:00:00", 0);

        svc.runOnce(dt(SCAN));
        // 又来一个候补；再次扫号不能把已转号翻出来重转
        waiter(11, "Q1", "2026-10-08 11:00:00", 0);
        RegRunResult r2 = svc.runOnce(dt("2026-10-08 15:00:00"));

        assertEquals(0, r2.getDueCount());
        assertEquals(0, r2.getTransferredCount());
        assertEquals(Long.valueOf(10L), taskMapper.selectById(1L).getWaitId());
        assertEquals(1, logCount(1));
    }

    /** 流感高峰单开档不走日常档；同档同号当天二次进队列直接忽略，先前那次原样保留 */
    @Test
    void fluWindow_separateTrack_andSameDayDuplicateIgnored() {
        // 白天档与流感档同一时刻都开着；流感档必须说话
        TMhRegWindow flu = new TMhRegWindow();
        flu.setId(1L);
        flu.setWindowCode("FLU");
        flu.setWindowName("流感高峰档");
        flu.setWindowType(1);
        flu.setWeekdayMask("1111111");
        flu.setOpenAt("08:00:00");
        flu.setCloseAt("20:00:00");
        flu.setPriority(0);
        flu.setStatus(0);
        flu.setDelFlag(0);
        windowMapper.insert(flu);

        TMhRegTask t = task(1, "QF", null, "2026-10-08 08:30:00", "0");
        waiter(10, "QF", "2026-10-08 07:00:00", 0);

        RegRunResult r1 = svc.runOnce(dt("2026-10-08 09:00:00"));
        assertEquals("FLU", r1.getWindowCode());
        assertEquals(Integer.valueOf(1), r1.getWindowType());
        assertEquals(1, r1.getTransferredCount());

        // 当天二次进队列：状态被外部置回待转（重投），先前台账保留
        t.setStatus(0);
        t.setWaitId(null);
        t.setTransferredAt(null);
        taskMapper.updateById(t);
        waiter(11, "QF", "2026-10-08 11:00:00", 0);

        RegRunResult r2 = svc.runOnce(dt("2026-10-08 12:00:00"));
        assertEquals("FLU", r2.getWindowCode());
        assertEquals(1, r2.getDueCount());
        assertEquals(1, r2.getSkippedDuplicateCount());
        assertEquals(0, r2.getTransferredCount());
        assertEquals(1, r2.getWaitingCount());
        // 号仍落待转，但先前那次转出原样保留（仍只 1 条台账、承接人快照在台账里）
        assertEquals(Integer.valueOf(0), taskMapper.selectById(1L).getStatus());
        assertEquals(1, logCount(1));
        assertEquals("W10", logMapper.selectOne(new QueryWrapper<TMhRegLog>()
                .eq("task_id", 1L).eq("action", 1)).getWaiterNo());
    }

    /** 三种停摆 → 就地挂起写原由；挂一个不拖整轮，其余照常转 */
    @Test
    void closures_suspendWithReason_andRoundContinues() {
        task(1, "QD", "D1", "2026-10-08 09:00:00", "0"); // 医生休诊
        task(2, "QD", null, "2026-10-08 09:00:00", "0");  // 科室停摆
        task(3, "QC", null, "2026-10-08 09:00:00", "0");  // 候补整队列撤了
        task(4, "QN", null, "2026-10-08 09:00:00", "0");  // 正常号
        waiter(10, "QD", "2026-10-01 08:00:00", 0);
        waiter(11, "QC", "2026-10-01 08:00:00", 0);
        waiter(12, "QN", "2026-10-01 08:00:00", 0);

        closure(20, 1, "D1", null, "2026-10-08 00:00:00", null);
        closure(21, 2, null, "QD", "2026-10-08 00:00:00", null);
        closure(22, 3, null, "QC", "2026-10-08 00:00:00", null);

        RegRunResult r = svc.runOnce(dt(SCAN));

        assertEquals(4, r.getDueCount());
        assertEquals(1, r.getTransferredCount());
        assertEquals(3, r.getSuspendedCount());
        assertEquals(0, r.getWaitingCount());
        assertFalse(r.isError());
        assertEquals(Integer.valueOf(3), taskMapper.selectById(1L).getStatus());
        assertEquals(Integer.valueOf(3), taskMapper.selectById(2L).getStatus());
        assertEquals(Integer.valueOf(3), taskMapper.selectById(3L).getStatus());
        assertTrue(taskMapper.selectById(1L).getSuspendReason().contains("医生休诊"));
        assertTrue(taskMapper.selectById(2L).getSuspendReason().contains("科室停摆"));
        assertTrue(taskMapper.selectById(3L).getSuspendReason().contains("候补整队列撤了"));
        assertEquals(Integer.valueOf(1), taskMapper.selectById(4L).getStatus());
        assertEquals(3, logCount(2));
        // 停摆期间候补不被占用
        assertEquals(Integer.valueOf(0), waitMapper.selectById(10L).getStatus());
    }

    /** 挂起的不再参与后续转号 */
    @Test
    void suspended_neverReconsidered() {
        TMhRegTask t = task(1, "Q1", null, "2026-10-08 09:00:00", "0");
        waiter(10, "Q1", "2026-10-01 08:00:00", 0);
        closure(20, 2, null, "Q1", "2026-10-08 00:00:00", null);

        svc.runOnce(dt(SCAN));
        assertEquals(Integer.valueOf(3), taskMapper.selectById(1L).getStatus());

        // 停摆解除、候补仍在；下一轮不得把挂起号翻出来
        TMhRegClosure c = closureMapper.selectById(20L);
        c.setStatus(1);
        closureMapper.updateById(c);
        RegRunResult r2 = svc.runOnce(dt("2026-10-09 10:00:00"));
        assertEquals(0, r2.getDueCount());
        assertEquals(0, r2.getTransferredCount());
        assertEquals(Integer.valueOf(3), taskMapper.selectById(1L).getStatus());
        assertEquals(Integer.valueOf(0), waitMapper.selectById(10L).getStatus());
    }

    /** 撤号留痕；已转号不许撤；最早放号日/批次不被覆写 */
    @Test
    void cancel_leavesTrace_andProtectsDone() {
        TMhRegTask t = task(1, "Q1", null, "2026-10-08 09:00:00", "0");
        assertEquals(1, svc.cancelTask(1L, "超期三天转不出去"));
        TMhRegTask after = taskMapper.selectById(1L);
        assertEquals(Integer.valueOf(1), after.getDelFlag());
        assertEquals(Integer.valueOf(2), after.getStatus());
        assertEquals("REL1", after.getReleaseNo());
        assertEquals(dt("2026-09-01 09:00:00"), after.getCreateTime());
        TMhRegLog log = logMapper.selectOne(new QueryWrapper<TMhRegLog>().eq("task_id", 1L));
        assertEquals(Integer.valueOf(3), log.getAction());
        assertTrue(log.getReason().contains("超期三天"));

        // 已转出的号撤不动，且转出时刻不许被覆盖
        task(2, "Q2", null, "2026-10-08 09:00:00", "0");
        waiter(10, "Q2", "2026-10-01 08:00:00", 0);
        svc.runOnce(dt(SCAN));
        Date transferredAt = taskMapper.selectById(2L).getTransferredAt();
        assertNotNull(transferredAt);
        assertEquals(0, svc.cancelTask(2L, "想撤已转号"));
        assertEquals(Integer.valueOf(1), taskMapper.selectById(2L).getStatus());
        assertEquals(Integer.valueOf(0), taskMapper.selectById(2L).getDelFlag());
        assertEquals(transferredAt, taskMapper.selectById(2L).getTransferredAt());
        assertEquals("REL2", taskMapper.selectById(2L).getReleaseNo());

        // 软删号不再被扫号捞到
        assertEquals(0, svc.runOnce(dt("2026-10-09 10:00:00")).getDueCount());
    }

    /** 改用途留痕，落终态，放号日/转出时刻列不被触碰 */
    @Test
    void repurpose_leavesTrace_doneProtected() {
        task(1, "Q1", null, "2026-10-08 09:00:00", "0");
        assertEquals(1, svc.repurposeTask(1L, "改为教学示教号", "改用途"));
        TMhRegTask after = taskMapper.selectById(1L);
        assertEquals(Integer.valueOf(2), after.getStatus());
        assertEquals("改为教学示教号", after.getContent());
        assertEquals("REL1", after.getReleaseNo());
        assertNull(after.getTransferredAt());
        assertEquals(Integer.valueOf(4),
                logMapper.selectOne(new QueryWrapper<TMhRegLog>().eq("task_id", 1L)).getAction());

        task(2, "Q2", null, "2026-10-08 09:00:00", "0");
        waiter(10, "Q2", "2026-10-01 08:00:00", 0);
        svc.runOnce(dt(SCAN));
        assertEquals(0, svc.repurposeTask(2L, "想改已转号", "x"));
        assertEquals(Integer.valueOf(1), taskMapper.selectById(2L).getStatus());
    }

    /** 一屏三个数只算本轮、出自同一次点算：2 转 / 2 挂 / 1 仍待转 */
    @Test
    void screenCounts_onlyThisRun_oneCounting() {
        task(1, "QA", null, "2026-10-08 09:00:00", "0");
        task(2, "QA", null, "2026-10-08 09:00:00", "0");
        task(3, "QS", null, "2026-10-08 09:00:00", "0");
        task(4, "QS", null, "2026-10-08 09:00:00", "0");
        task(5, "QEMPTY", null, "2026-10-08 09:00:00", "0"); // 候补排空 → 仍待转
        waiter(10, "QA", "2026-10-01 08:00:00", 0);
        waiter(11, "QA", "2026-10-01 09:00:00", 0);
        closure(20, 2, null, "QS", "2026-10-08 00:00:00", null);

        RegRunResult r = svc.runOnce(dt(SCAN));

        assertEquals(5, r.getDueCount());
        assertEquals(2, r.getTransferredCount());
        assertEquals(2, r.getSuspendedCount());
        assertEquals(1, r.getWaitingCount());
        // 屏上的数与库内同一次点算同源：直接数库应一致
        assertEquals(Integer.valueOf(2), logMapper.selectCount(new QueryWrapper<TMhRegLog>()
                .eq("run_batch", r.getBatchNo()).eq("action", 1)));
        assertEquals(Integer.valueOf(2), logMapper.selectCount(new QueryWrapper<TMhRegLog>()
                .eq("run_batch", r.getBatchNo()).eq("action", 2)));
        assertEquals(Integer.valueOf(0), taskMapper.selectById(5L).getStatus());
    }

    /** 浮出边界：离截止还剩 amount 个自然日当天浮出；提前量按整日 */
    @Test
    void surface_boundaryByNaturalDays() {
        // 截止 10-11 18:00，提前 3 天 → 10-08 18:00 浮出；10:00 扫描未到
        task(1, "Q1", null, "2026-10-11 18:00:00", "3");
        waiter(10, "Q1", "2026-10-01 08:00:00", 0);
        RegRunResult early = svc.runOnce(dt(SCAN));
        assertEquals(0, early.getDueCount());

        // 同号在 18:00 扫描即已浮出，正常转出
        RegRunResult onTime = svc.runOnce(dt("2026-10-08 18:00:00"));
        assertEquals(1, onTime.getDueCount());
        assertEquals(1, onTime.getTransferredCount());
    }

    /** 超期三天提最前：老号与新号争同一个候补位，老号先得 */
    @Test
    void overdueThreeDays_movedToFront() {
        // A 五天前就该浮出（至今未转）；B 今天才浮出；队列只有一个候补
        task(1, "Q1", null, "2026-10-03 09:00:00", "0");
        task(2, "Q1", null, "2026-10-08 09:00:00", "0");
        waiter(10, "Q1", "2026-10-01 08:00:00", 0);

        RegRunResult r = svc.runOnce(dt(SCAN));
        assertEquals(1, r.getTransferredCount());
        assertEquals(1, r.getWaitingCount());
        assertEquals(Long.valueOf(10L), taskMapper.selectById(1L).getWaitId());
        assertEquals(Integer.valueOf(0), taskMapper.selectById(2L).getStatus());
    }
}
