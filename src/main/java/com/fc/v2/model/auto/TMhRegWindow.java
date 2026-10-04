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
 * 值班划的扫号窗口 对象 t_mh_reg_window
 *
 * @author fuce
 * @date 2026-10-04
 */
@TableName("t_mh_reg_window")
@ApiModel(value = "TMhRegWindow", description = "t_mh_reg_window")
public class TMhRegWindow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 扫号窗口代号 */
    @TableField("window_code")
    @ApiModelProperty(value = "扫号窗口代号")
    private String windowCode;

    /** 窗口名（值班划的） */
    @TableField("window_name")
    @ApiModelProperty(value = "窗口名（值班划的）")
    private String windowName;

    /** 窗口档别 0日常 1流感高峰单开档 */
    @TableField("window_type")
    @ApiModelProperty(value = "窗口档别 0日常 1流感高峰单开档")
    private Integer windowType;

    /** 开扫星期，7位0/1依次周一..周日 */
    @TableField("weekday_mask")
    @ApiModelProperty(value = "开扫星期，7位0/1依次周一..周日")
    private String weekdayMask;

    /** 开窗时刻 HH:mm:ss */
    @TableField("open_at")
    @ApiModelProperty(value = "开窗时刻 HH:mm:ss")
    private String openAt;

    /** 关窗时刻 HH:mm:ss（含） */
    @TableField("close_at")
    @ApiModelProperty(value = "关窗时刻 HH:mm:ss（含）")
    private String closeAt;

    /** 档别启用之日 */
    @TableField("eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "档别启用之日")
    private Date effStart;

    /** 档别交棒之日(不含)，空为长期 */
    @TableField("eff_end")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "档别交棒之日(不含)，空为长期")
    private Date effEnd;

    /** 档别撞期时的说话顺位（数值大者先判定） */
    @TableField("priority")
    @ApiModelProperty(value = "档别撞期时的说话顺位（数值大者先判定）")
    private Integer priority;

    /** 窗口情形 0现行 1已停用 */
    @TableField("status")
    @ApiModelProperty(value = "窗口情形 0现行 1已停用")
    private Integer status;

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

    public String getWindowCode() {
        return windowCode;
    }

    public void setWindowCode(String windowCode) {
        this.windowCode = windowCode;
    }

    public String getWindowName() {
        return windowName;
    }

    public void setWindowName(String windowName) {
        this.windowName = windowName;
    }

    public Integer getWindowType() {
        return windowType;
    }

    public void setWindowType(Integer windowType) {
        this.windowType = windowType;
    }

    public String getWeekdayMask() {
        return weekdayMask;
    }

    public void setWeekdayMask(String weekdayMask) {
        this.weekdayMask = weekdayMask;
    }

    public String getOpenAt() {
        return openAt;
    }

    public void setOpenAt(String openAt) {
        this.openAt = openAt;
    }

    public String getCloseAt() {
        return closeAt;
    }

    public void setCloseAt(String closeAt) {
        this.closeAt = closeAt;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public Date getEffEnd() {
        return effEnd;
    }

    public void setEffEnd(Date effEnd) {
        this.effEnd = effEnd;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
