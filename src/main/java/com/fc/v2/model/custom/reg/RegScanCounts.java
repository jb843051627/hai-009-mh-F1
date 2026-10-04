package com.fc.v2.model.custom.reg;

import java.io.Serializable;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 一轮扫完后的同一次点算结果（一条 SQL 出三个数）。
 * 屏上的待转/已转/挂起只认这一份，不许用内存计数各拼各的。
 *
 * @author fuce
 * @date 2026-10-04
 */
@ApiModel(value = "RegScanCounts", description = "本轮点算三计数")
public class RegScanCounts implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 本轮到期号扫完后仍落待转的条数 */
    @ApiModelProperty(value = "待转条数")
    private Integer waitingCount;

    /** 本轮真转出去的条数（台账 action=1） */
    @ApiModelProperty(value = "已转条数")
    private Integer transferredCount;

    /** 本轮就地挂起的条数（台账 action=2） */
    @ApiModelProperty(value = "挂起条数")
    private Integer suspendedCount;

    public Integer getWaitingCount() {
        return waitingCount;
    }

    public void setWaitingCount(Integer waitingCount) {
        this.waitingCount = waitingCount;
    }

    public Integer getTransferredCount() {
        return transferredCount;
    }

    public void setTransferredCount(Integer transferredCount) {
        this.transferredCount = transferredCount;
    }

    public Integer getSuspendedCount() {
        return suspendedCount;
    }

    public void setSuspendedCount(Integer suspendedCount) {
        this.suspendedCount = suspendedCount;
    }
}
