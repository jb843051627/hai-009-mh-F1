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
 * t_mh_scan_round 对象 t_mh_scan_round
 * 扫号轮次结账台：待转/已转/挂起只算这一轮；屏上三个数和库里的数出自同一次点算。
 *
 * @author fuce
 * @date 2026-10-03
 */
@TableName("t_mh_scan_round")
@ApiModel(value = "TMhScanRound", description = "t_mh_scan_round")
public class TMhScanRound implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 轮次号 */
    @TableField("round_no")
    @ApiModelProperty(value = "轮次号")
    private String roundNo;

    /** 本轮所开窗口档 */
    @TableField("win_code")
    @ApiModelProperty(value = "本轮所开窗口档")
    private String winCode;

    /** 本轮点算时刻 */
    @TableField("scan_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "本轮点算时刻")
    private Date scanAt;

    /** 本轮到期条数（窗口外点算为 0，不动号） */
    @TableField("due_count")
    @ApiModelProperty(value = "本轮到期条数")
    private Integer dueCount;

    /** 本轮结账：待转几条 */
    @TableField("wait_count")
    @ApiModelProperty(value = "本轮结账：待转几条")
    private Integer waitCount;

    /** 本轮结账：已转几条 */
    @TableField("done_count")
    @ApiModelProperty(value = "本轮结账：已转几条")
    private Integer doneCount;

    /** 本轮结账：挂起几条 */
    @TableField("hold_count")
    @ApiModelProperty(value = "本轮结账：挂起几条")
    private Integer holdCount;

    /** 本轮情形 0无活 1已执行 2出错 */
    @TableField("run_status")
    @ApiModelProperty(value = "本轮情形 0无活 1已执行 2出错")
    private Integer runStatus;

    /** 出错信息（无活与出错绝不能混为一谈） */
    @TableField("err_msg")
    @ApiModelProperty(value = "出错信息")
    private String errMsg;

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

    public String getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(String roundNo) {
        this.roundNo = roundNo;
    }

    public String getWinCode() {
        return winCode;
    }

    public void setWinCode(String winCode) {
        this.winCode = winCode;
    }

    public Date getScanAt() {
        return scanAt;
    }

    public void setScanAt(Date scanAt) {
        this.scanAt = scanAt;
    }

    public Integer getDueCount() {
        return dueCount;
    }

    public void setDueCount(Integer dueCount) {
        this.dueCount = dueCount;
    }

    public Integer getWaitCount() {
        return waitCount;
    }

    public void setWaitCount(Integer waitCount) {
        this.waitCount = waitCount;
    }

    public Integer getDoneCount() {
        return doneCount;
    }

    public void setDoneCount(Integer doneCount) {
        this.doneCount = doneCount;
    }

    public Integer getHoldCount() {
        return holdCount;
    }

    public void setHoldCount(Integer holdCount) {
        this.holdCount = holdCount;
    }

    public Integer getRunStatus() {
        return runStatus;
    }

    public void setRunStatus(Integer runStatus) {
        this.runStatus = runStatus;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
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
