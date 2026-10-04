package com.fc.v2.mapper.custom;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.fc.v2.model.custom.reg.RegScanCounts;

/**
 * 转号扫号专用的合并点算：
 * 待转（本轮到期号里仍落 0 的）、已转、挂起三个数必须出自同一条 SQL、同一次点算，
 * 屏上展示与库内实数才不会各说各话。
 *
 * @author fuce
 * @date 2026-10-04
 */
public interface RegScanDao {

    /**
     * 一条 SQL 出三计数。
     * @param snapshotIds 本轮开扫时点到的到期号 id 快照
     * @param batchNo 本轮批次号（台账对账口径）
     */
    @Select("<script>"
            + "SELECT "
            + " (SELECT COUNT(*) FROM t_mh_reg_task WHERE del_flag = 0 AND status = 0"
            + "  <if test='ids != null and ids.size() > 0'> AND id IN "
            + "  <foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></if>"
            + " ) AS waiting_count, "
            + " (SELECT COUNT(*) FROM t_mh_reg_log WHERE del_flag = 0 AND action = 1 AND run_batch = #{batchNo}"
            + " ) AS transferred_count, "
            + " (SELECT COUNT(*) FROM t_mh_reg_log WHERE del_flag = 0 AND action = 2 AND run_batch = #{batchNo}"
            + " ) AS suspended_count "
            + "</script>")
    RegScanCounts recountThisRun(@Param("ids") List<Long> snapshotIds, @Param("batchNo") String batchNo);
}
