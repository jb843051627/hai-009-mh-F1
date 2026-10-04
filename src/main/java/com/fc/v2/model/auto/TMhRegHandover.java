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
 * t_mh_reg_handover 对象 t_mh_reg_handover
 * 号源转号交接留痕：谁接了、什么时候接的；挂起/撤号/改用途同样留痕，只追加不改写。
 *
 * @author fuce
 * @date 2026-10-03
 */
@TableName("t_mh_reg_handover")
@ApiModel(value = "TMhRegHandover", description = "t_mh_reg_handover")
public class TMhRegHandover implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 转号事项（t_mh_reg_task.id） */
    @TableField("reg_id")
    @ApiModelProperty(value = "转号事项")
    private Long regId;

    /** 事项号（冗余留痕，事项删了也查得到） */
    @TableField("item_no")
    @ApiModelProperty(value = "事项号")
    private String itemNo;

    /** 交接动作 1转成 2挂起 3撤号 4改用途 */
    @TableField("act_type")
    @ApiModelProperty(value = "交接动作 1转成 2挂起 3撤号 4改用途")
    private Integer actType;

    /** 落定所在扫号窗口档（同档同号只转一次据此判） */
    @TableField("win_code")
    @ApiModelProperty(value = "落定所在扫号窗口档")
    private String winCode;

    /** 接号候补条（转成时落） */
    @TableField("from_wait_id")
    @ApiModelProperty(value = "接号候补条")
    private Long fromWaitId;

    /** 接号人（谁接了） */
    @TableField("card_id")
    @ApiModelProperty(value = "接号人")
    private Long cardId;

    /** 动作时刻（什么时候接的/撤的） */
    @TableField("act_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "动作时刻")
    private Date actAt;

    /** 挂起原由/撤号或改用途说明 */
    @TableField("reason")
    @ApiModelProperty(value = "挂起原由/撤号或改用途说明")
    private String reason;

    /** 所属扫号轮次号 */
    @TableField("round_no")
    @ApiModelProperty(value = "所属扫号轮次号")
    private String roundNo;

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

    public Long getRegId() {
        return regId;
    }

    public void setRegId(Long regId) {
        this.regId = regId;
    }

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public Integer getActType() {
        return actType;
    }

    public void setActType(Integer actType) {
        this.actType = actType;
    }

    public String getWinCode() {
        return winCode;
    }

    public void setWinCode(String winCode) {
        this.winCode = winCode;
    }

    public Long getFromWaitId() {
        return fromWaitId;
    }

    public void setFromWaitId(Long fromWaitId) {
        this.fromWaitId = fromWaitId;
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public Date getActAt() {
        return actAt;
    }

    public void setActAt(Date actAt) {
        this.actAt = actAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(String roundNo) {
        this.roundNo = roundNo;
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
