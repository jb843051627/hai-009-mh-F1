package com.fc.v2.model.custom.reg;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 一轮扫号结账回执。
 *
 * 三句话先分清，去年闹过误会：
 * 1) executed=false：那一刻根本不在值班划的扫号窗口里（夜间/法定节假日整段不扫），
 *    库里一个字节都没动，到期的号原样等下轮窗口——这不是报错；
 * 2) executed=true 且 dueCount=0：窗口里真扫了，这一轮没有到期的号——明说"本轮无活"，
 *    同样不是报错；
 * 3) error=true：这一轮确实有号处理失败（落库异常等），看 message/失败数对账。
 *
 * 屏上的"待转/已转/挂起"三个数只算这一轮，且来自同一条 GROUP BY 点算，
 * 与库里逐条翻出来的数同源，不允许各算各的。
 *
 * @author fuce
 * @date 2026-10-04
 */
@ApiModel(value = "RegRunResult", description = "一轮扫号结账回执")
public class RegRunResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 这一轮是否真的扫了（false=窗口外，任何状态都没动） */
    @ApiModelProperty(value = "是否在窗口内执行")
    private boolean executed;

    /** 本轮扫号批次号（同一屏同一批，台账按它对账） */
    @ApiModelProperty(value = "本轮扫号批次号")
    private String batchNo;

    /** 扫号时刻（调用方指定的墙钟，定时任务传 now） */
    @ApiModelProperty(value = "扫号时刻")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date at;

    /** 命中的扫号窗口代号（窗口外为 null） */
    @ApiModelProperty(value = "命中的扫号窗口代号")
    private String windowCode;

    /** 命中窗口档别 0日常 1流感高峰单开档 */
    @ApiModelProperty(value = "命中窗口档别 0日常 1流感高峰")
    private Integer windowType;

    /** 这一轮点到的到期号总数（处理前快照口径） */
    @ApiModelProperty(value = "本轮到期号总数")
    private int dueCount;

    /** 待转：本轮到期号扫完后仍落待转的（没人接/被档规跳过） */
    @ApiModelProperty(value = "待转条数")
    private int waitingCount;

    /** 已转：本轮到期号中真转出去的（状态与转出时刻同落） */
    @ApiModelProperty(value = "已转条数")
    private int transferredCount;

    /** 挂起：本轮到期号中就地挂起的（休诊/停摆/撤队列） */
    @ApiModelProperty(value = "挂起条数")
    private int suspendedCount;

    /** 流感档当天二次进队列被忽略的条数（先前转出原样保留） */
    @ApiModelProperty(value = "当天重复忽略条数")
    private int skippedDuplicateCount;

    /** 这一轮落库失败的条数（挂一个不拖整轮，其余照常） */
    @ApiModelProperty(value = "失败条数")
    private int failedCount;

    /** 这一轮是否出过处理差错（区别于"本轮无活"） */
    @ApiModelProperty(value = "是否有处理差错")
    private boolean error;

    /** 结账说明：窗口外/本轮无活/完成/差错提示 */
    @ApiModelProperty(value = "结账说明")
    private String message;

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Date getAt() {
        return at;
    }

    public void setAt(Date at) {
        this.at = at;
    }

    public String getWindowCode() {
        return windowCode;
    }

    public void setWindowCode(String windowCode) {
        this.windowCode = windowCode;
    }

    public Integer getWindowType() {
        return windowType;
    }

    public void setWindowType(Integer windowType) {
        this.windowType = windowType;
    }

    public int getDueCount() {
        return dueCount;
    }

    public void setDueCount(int dueCount) {
        this.dueCount = dueCount;
    }

    public int getWaitingCount() {
        return waitingCount;
    }

    public void setWaitingCount(int waitingCount) {
        this.waitingCount = waitingCount;
    }

    public int getTransferredCount() {
        return transferredCount;
    }

    public void setTransferredCount(int transferredCount) {
        this.transferredCount = transferredCount;
    }

    public int getSuspendedCount() {
        return suspendedCount;
    }

    public void setSuspendedCount(int suspendedCount) {
        this.suspendedCount = suspendedCount;
    }

    public int getSkippedDuplicateCount() {
        return skippedDuplicateCount;
    }

    public void setSkippedDuplicateCount(int skippedDuplicateCount) {
        this.skippedDuplicateCount = skippedDuplicateCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
