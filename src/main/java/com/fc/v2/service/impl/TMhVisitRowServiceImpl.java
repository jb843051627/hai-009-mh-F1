package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMhVisitRowMapper;
import com.fc.v2.model.auto.TMhVisitRow;
import com.fc.v2.service.ITMhVisitRowService;

/**
 * 门诊就诊记录导入核对行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMhVisitRowServiceImpl implements ITMhVisitRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;
    /** 单行明细码长度上限 */
    private static final int ITEM_CODE_MAX = 64;
    /** 单行数量上限（超出视为录入错误，防止脏数据拉爆统计） */
    private static final java.math.BigDecimal QTY_MAX = new java.math.BigDecimal("999999");

    /** 批次处理留痕（单机内存缓冲，供运维查"这批发过没有"） */
    private final java.util.Map<String, String> batchStats =
            new java.util.concurrent.ConcurrentHashMap<String, String>();

    @javax.annotation.Resource
    private TMhVisitRowMapper mhVisitRowMapper;

    @Override
    public TMhVisitRow selectTMhVisitRowById(Long id) {
        return this.mhVisitRowMapper.selectById(id);
    }

    @Override
    public int submitBatch(String batchNo, List<TMhVisitRow> rows) {
        // ① 批次级前置校验：批次号或明细缺失即"空批"，安全返回 0。
        //    不能像朴素实现那样 rows.get(0) 取批次号：空批会直接越界抛异常。
        if (isBlank(batchNo) || rows == null || rows.isEmpty()) {
            return 0;
        }
        // ② 单批行数上限：超限整批拒收（返回 -1），且一行都不得入库。
        //    必须先判上限再落任何一条，否则会出现"拒收了但库里已有半批"的脏状态。
        if (rows.size() > MAX_ROWS) {
            note(batchNo, "批次超限拒收：" + rows.size() + " 行");
            return -1;
        }
        // ③ 重放判定：同批次号已有明细（成功、失败皆算）视为重复提交，直接返回 0。
        //    重放路径必须只读不写，否则重放会重复入库。
        if (countBatch(batchNo) > 0) {
            note(batchNo, "批次已提交过，按幂等忽略");
            return 0;
        }
        java.util.Set<String> seen = new java.util.HashSet<String>();
        java.util.Set<Integer> usedRowNo = new java.util.HashSet<Integer>();
        int ok = 0;
        int seq = 0;
        for (TMhVisitRow r : rows) {
            seq++;
            if (r == null) {
                // 整行为 null：没有载体可留痕，只能跳过，且不影响其它行
                continue;
            }
            // ④ 原始行号：必须保留调用方给的 Excel 行号（失败明细要能定位到具体行）。
            //    只有缺失/冲突时才补一个未占用序号，避免失败明细排序错乱。
            int rowNo = normalizeRowNo(r, usedRowNo, seq);
            // ⑤ 逐行校验：单条非法不中断整批 —— 合法行照常入库，非法行落到失败明细。
            String invalid = invalidReason(r);
            if (invalid != null) {
                markFail(r, batchNo, rowNo, invalid);
                continue;
            }
            // ⑥ 批内去重：同一明细码只取第一条（重复码在业务上是同一笔，不能重复入库）。
            String code = r.getItemCode().trim();
            if (!seen.add(code)) {
                note(batchNo, "第" + rowNo + "行明细码重复，已跳过：" + code);
                continue;
            }
            markOk(r, batchNo, rowNo);
            ok++;
        }
        note(batchNo, "提交完成：成功 " + ok + " 行");
        return ok;
    }

    @Override
    public List<TMhVisitRow> listErrors(String batchNo) {
        if (isBlank(batchNo)) {
            return new java.util.ArrayList<TMhVisitRow>();
        }
        // 只取该批次的失败明细（成功行从不进失败清单），口径与 submitBatch 的落库口径一致
        List<TMhVisitRow> errs = this.mhVisitRowMapper.selectList(new QueryWrapper<TMhVisitRow>()
                .eq("batch_no", batchNo)
                .eq("status", STATUS_FAIL)
                .eq("del_flag", 0));
        if (errs == null || errs.isEmpty()) {
            return new java.util.ArrayList<TMhVisitRow>();
        }
        // 按原始行号升序：运维要按 Excel 行序逐行修，乱序会漏改
        errs.sort(new java.util.Comparator<TMhVisitRow>() {
            @Override
            public int compare(TMhVisitRow a, TMhVisitRow b) {
                int ra = a.getRowNo() == null ? 0 : a.getRowNo().intValue();
                int rb = b.getRowNo() == null ? 0 : b.getRowNo().intValue();
                return ra - rb;
            }
        });
        return errs;
    }
    /** 批次是否已落过明细（成功或失败都算），用于重放判定 */
    private int countBatch(String batchNo) {
        List<TMhVisitRow> exist = this.mhVisitRowMapper.selectList(
                new QueryWrapper<TMhVisitRow>().eq("batch_no", batchNo));
        return exist == null ? 0 : exist.size();
    }

    /** 空串判定：批次号/明细码的纯空白串按"缺失"处理 */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 单行校验：返回 null 表示合法；否则返回失败原因（原因写入失败明细备注，便于运维定位） */
    private String invalidReason(TMhVisitRow r) {
        if (isBlank(r.getItemCode())) {
            return "明细码为空";
        }
        if (r.getItemCode().trim().length() > ITEM_CODE_MAX) {
            return "明细码超过 " + ITEM_CODE_MAX + " 个字符";
        }
        if (r.getQty() == null) {
            return "数量缺失";
        }
        if (r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return "数量必须大于 0";
        }
        if (r.getQty().compareTo(QTY_MAX) > 0) {
            return "数量超过单行上限 " + QTY_MAX.toPlainString();
        }
        return null;
    }

    /** 原始行号归一：优先保留调用方给的 Excel 行号，缺失/冲突时才补一个未占用序号 */
    private static int normalizeRowNo(TMhVisitRow r, java.util.Set<Integer> used, int fallback) {
        Integer given = r.getRowNo();
        if (given != null && given.intValue() > 0 && used.add(given)) {
            return given.intValue();
        }
        int candidate = fallback > 0 ? fallback : 1;
        while (!used.add(Integer.valueOf(candidate))) {
            candidate++;
        }
        return candidate;
    }

    /** 失败行落库：状态置失败 + 原因写进备注，原始行号原样保留 */
    private void markFail(TMhVisitRow r, String batchNo, int rowNo, String reason) {
        r.setBatchNo(batchNo);
        r.setRowNo(Integer.valueOf(rowNo));
        r.setStatus(STATUS_FAIL);
        r.setRemark(reason);
        stamp(r);
        this.mhVisitRowMapper.insert(r);
    }

    /** 成功行落库：状态置成功，并清掉失败备注（同一实体可能被复用，避免残留上次原因） */
    private void markOk(TMhVisitRow r, String batchNo, int rowNo) {
        r.setBatchNo(batchNo);
        r.setRowNo(Integer.valueOf(rowNo));
        r.setStatus(STATUS_OK);
        r.setRemark(null);
        stamp(r);
        this.mhVisitRowMapper.insert(r);
    }

    /** 写前盖章：软删标记缺省补 0，更新时间统一由服务端生成 */
    private static void stamp(TMhVisitRow r) {
        if (r.getDelFlag() == null) {
            r.setDelFlag(0);
        }
        r.setUpdateTime(new java.util.Date());
    }

    /** 整批处理留痕：按批次记录最近一次处理结论（内存缓冲，超过 200 批整体清理） */
    private void note(String batchNo, String what) {
        if (batchStats.size() > 200) {
            batchStats.clear();
        }
        batchStats.put(batchNo, what);
    }

}
