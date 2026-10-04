package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 号源转号台账（谁接的/何时接的/撤号留痕） 对象 t_mh_reg_log
 *
 * @author fuce
 * @date 2026-10-04
 */
@TableName("t_mh_reg_log")
@ApiModel(value = "TMhRegLog", description = "t_mh_reg_log")
public class TMhRegLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 号源事项条id（哪一个号） */
    @TableField("task_id")
    @ApiModelProperty(value = "号源事项条id（哪一个号）")
    private Long taskId;

    /** 号源转号事项号（快照留痕） */
    @TableField("item_no")
    @ApiModelProperty(value = "号源转号事项号（快照留痕）")
    private String itemNo;

    /** 最早定下的放号日批次号（快照留痕） */
    @TableField("release_no")
    @ApiModelProperty(value = "最早定下的放号日批次号（快照留痕）")
    private String releaseNo;

    /** 候补队列代号 */
    @TableField("queue_key")
    @ApiModelProperty(value = "候补队列代号")
    private String queueKey;

    /** 承接的候补排队人id（谁接的） */
    @TableField("wait_id")
    @ApiModelProperty(value = "承接的候补排队人id（谁接的）")
    private Long waitId;

    /** 承接人代号（快照留痕） */
    @TableField("waiter_no")
    @ApiModelProperty(value = "承接人代号（快照留痕）")
    private String waiterNo;

    /** 落账时所在扫号窗口档 */
    @TableField("window_code")
    @ApiModelProperty(value = "落账时所在扫号窗口档")
    private String windowCode;

    /** 本轮扫号批次号（同一屏同一批） */
    @TableField("run_batch")
    @ApiModelProperty(value = "本轮扫号批次号（同一屏同一批）")
    private String runBatch;

    /** 动作 1转出 2挂起 3撤号 4改用途 */
    @TableField("action")
    @ApiModelProperty(value = "动作 1转出 2挂起 3撤号 4改用途")
    private Integer action;

    /** 原由（挂起/撤号/改用途留痕） */
    @TableField("reason")
    @ApiModelProperty(value = "原由（挂起/撤号/改用途留痕）")
    private String reason;

    /** 动作落账时刻（转出即真转出时刻） */
    @TableField("acted_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "动作落账时刻（转出即真转出时刻）")
    private Date actedAt;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public String getReleaseNo() {
        return releaseNo;
    }

    public void setReleaseNo(String releaseNo) {
        this.releaseNo = releaseNo;
    }

    public String getQueueKey() {
        return queueKey;
    }

    public void setQueueKey(String queueKey) {
        this.queueKey = queueKey;
    }

    public Long getWaitId() {
        return waitId;
    }

    public void setWaitId(Long waitId) {
        this.waitId = waitId;
    }

    public String getWaiterNo() {
        return waiterNo;
    }

    public void setWaiterNo(String waiterNo) {
        this.waiterNo = waiterNo;
    }

    public String getWindowCode() {
        return windowCode;
    }

    public void setWindowCode(String windowCode) {
        this.windowCode = windowCode;
    }

    public String getRunBatch() {
        return runBatch;
    }

    public void setRunBatch(String runBatch) {
        this.runBatch = runBatch;
    }

    public Integer getAction() {
        return action;
    }

    public void setAction(Integer action) {
        this.action = action;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Date getActedAt() {
        return actedAt;
    }

    public void setActedAt(Date actedAt) {
        this.actedAt = actedAt;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
