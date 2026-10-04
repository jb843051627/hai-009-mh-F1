package com.fc.v2.common.quartz.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fc.v2.model.custom.reg.RegRunResult;
import com.fc.v2.service.ITMhRegTaskService;

/**
 * 号源到期转号扫号定时入口。
 *
 * 在值班划的扫号窗口内调用即可（窗口判定在服务内，窗口外配了也只会拿到 executed=false）。
 * 定时任务配置示例：regScanTask.runScan
 *
 * 三态都走日志，互不混淆：
 * - executed=false：窗口外（夜间/法定节假日），未动库，不是差错；
 * - executed=true 且 dueCount=0：本轮无活，不是差错；
 * - error=true：有条目处理失败，看失败条数对账。
 *
 * @author fuce
 * @date 2026-10-04
 */
@Component("regScanTask")
public class RegScanTask {

    private static final Logger log = LoggerFactory.getLogger(RegScanTask.class);

    @Autowired
    private ITMhRegTaskService regTaskService;

    /** 扫一轮（无参，墙钟取当下）；供 quartz 调度调用 */
    public void runScan() {
        RegRunResult r = regTaskService.runOnce();
        if (!r.isExecuted()) {
            log.info("[转号扫号] {}（批次={}）", r.getMessage(), r.getBatchNo());
            return;
        }
        if (r.isError()) {
            log.warn("[转号扫号] {}；批次={}，待转={}，已转={}，挂起={}，流感当天重复忽略={}",
                    r.getMessage(), r.getBatchNo(), r.getWaitingCount(), r.getTransferredCount(),
                    r.getSuspendedCount(), r.getSkippedDuplicateCount());
        } else {
            log.info("[转号扫号] {}；批次={}，待转={}，已转={}，挂起={}，流感当天重复忽略={}",
                    r.getMessage(), r.getBatchNo(), r.getWaitingCount(), r.getTransferredCount(),
                    r.getSuspendedCount(), r.getSkippedDuplicateCount());
        }
    }
}
