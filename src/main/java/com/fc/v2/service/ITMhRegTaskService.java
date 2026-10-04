package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TMhRegTask;
import com.fc.v2.model.auto.TMhWaitLine;

/**
 * TMhRegTask 业务层处理接口（scheduling-job 形状：周期执行）
 *
 * 转号铁律：
 * 1. 排号只认候补挂队列时刻；排空的优先；号超期三天转不出去置顶；普通挂号不许插队。
 * 2. 转号落定时"转出时刻 + 状态待转→已转"同生共死，缺一不算转成；下轮不得再当待转翻出。
 * 3. 扫号分窗口：白天一档，夜间与法定节假日整段不扫；流感高峰专档不走日常窗口，
 *    同一档里同一个号只转一次，当天二次进队列直接忽略，先前那次原样保留。
 * 4. 医生休诊/科室停摆/候补队列撤了 → 就地挂起写原由，挂起不拖整轮。
 * 5. 过期限仍转不出去只能撤号或改用途，留痕且最早放号日、真转出时刻永不覆盖。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMhRegTaskService {

    /** selectTMhRegTaskById */
    TMhRegTask selectTMhRegTaskById(Long id);

    /**
     * 到点该浮出来的待转号：浮出日 = due_at - amount（提前转号自然日数），浮出日含当日。
     * 只取未删除且状态为待转的条目；已转/挂起/撤号的一律不再浮出。
     */
    List<TMhRegTask> listDue(Date at);

    /**
     * 跑一轮扫号（日常白天窗口口径）。
     * 窗口外/法定节假日：不动号，结账 dueCount=0、runStatus=无活。
     * 窗口内但本轮无到期号：明说"本轮无活"，不得与执行出错混淆。
     */
    ScanSettle runOnce(Date at);

    /** 跑一轮扫号，显式指定窗口档代号（流感高峰专档走这里）。 */
    ScanSettle runOnce(Date at, String winCode);

    /** Quartz 值班调度入口：按当前墙钟自动裁定窗口（日常档/流感专档/窗口外）。 */
    ScanSettle scanNow();

    /**
     * 候补挂号：把一张卡挂上某号源的候补队列。
     * 当天二次进队列（同号源同人同自然日）直接忽略，返回 null，先前排队原样保留。
     */
    TMhWaitLine enqueue(Long regId, Long cardId, int queueType, Date at);

    /**
     * 放号落定：记下最早放号日。只落头一回，之后任何调用都不得覆盖该时刻。
     */
    TMhRegTask markRelease(Long regId, Date at);

    /** 候补整队列撤了（撤队原因写入后续挂起原由）。 */
    boolean withdrawQueue(Long regId, String reason);

    /** 转号前医生休诊/科室停摆：号就地挂起写原由，挂起的不再参与后续转号。 */
    TMhRegTask suspend(Long regId, String reason, Date at);

    /** 过了期限还转不出去：撤号留痕；最早放号日与真转出时刻不许被覆盖。 */
    TMhRegTask cancel(Long regId, String reason, Date at);

    /** 过了期限还转不出去：改用途留痕；时刻字段同样不许被覆盖。 */
    TMhRegTask repurpose(Long regId, String reason, Date at);

    /**
     * 扫完一屏结账：只算这一轮的待转/已转/挂起条数。
     * 屏上三个数与库里的数出自同一次点算，不做二次查询拼数。
     */
    class ScanSettle {
        /** 本轮情形 0无活 1已执行 2出错 */
        private int runStatus;
        /** 本轮候选（到期待转）条数；窗口外点算为 0 */
        private int dueCount;
        /** 本轮结账：待转 */
        private int waitCount;
        /** 本轮结账：已转 */
        private int doneCount;
        /** 本轮结账：挂起 */
        private int holdCount;
        /** 轮次号（对应 t_mh_scan_round.round_no） */
        private String roundNo;
        /** 本轮所开窗口档；窗口外为 null */
        private String winCode;
        /** 出错信息（仅 runStatus=2） */
        private String errMsg;

        public static ScanSettle idle(String roundNo, String winCode, int dueCount) {
            ScanSettle s = new ScanSettle();
            s.runStatus = 0;
            s.dueCount = dueCount;
            s.roundNo = roundNo;
            s.winCode = winCode;
            return s;
        }

        public static ScanSettle ok(String roundNo, String winCode, int dueCount,
                                    int waitCount, int doneCount, int holdCount) {
            ScanSettle s = new ScanSettle();
            s.runStatus = 1;
            s.dueCount = dueCount;
            s.waitCount = waitCount;
            s.doneCount = doneCount;
            s.holdCount = holdCount;
            s.roundNo = roundNo;
            s.winCode = winCode;
            return s;
        }

        public static ScanSettle error(String roundNo, String winCode, String errMsg) {
            ScanSettle s = new ScanSettle();
            s.runStatus = 2;
            s.errMsg = errMsg;
            s.roundNo = roundNo;
            s.winCode = winCode;
            return s;
        }

        public int getRunStatus() {
            return runStatus;
        }

        public int getDueCount() {
            return dueCount;
        }

        public int getWaitCount() {
            return waitCount;
        }

        public int getDoneCount() {
            return doneCount;
        }

        public int getHoldCount() {
            return holdCount;
        }

        public String getRoundNo() {
            return roundNo;
        }

        public String getWinCode() {
            return winCode;
        }

        public String getErrMsg() {
            return errMsg;
        }

        /** 无活与出错严格分开：这是给运维屏的一句话，去年就为这句话闹过误会 */
        public String message() {
            if (runStatus == 2) {
                return "本轮执行出错：" + (errMsg == null ? "" : errMsg);
            }
            if (runStatus == 0) {
                return dueCount == 0 && winCode == null ? "窗口外不扫，本轮无活" : "本轮无活";
            }
            return "本轮结账：待转 " + waitCount + "，已转 " + doneCount + "，挂起 " + holdCount;
        }
    }
}
