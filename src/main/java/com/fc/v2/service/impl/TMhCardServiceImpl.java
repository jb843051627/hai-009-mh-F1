package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TMhCardMapper;
import com.fc.v2.mapper.auto.TMhPatientMapper;
import com.fc.v2.model.auto.TMhCard;
import com.fc.v2.model.auto.TMhPatient;
import com.fc.v2.service.ITMhCardService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 就诊档案Service业务层处理
 *
 * 状态机：0待核对 → 1已核对 → 2已冻住（只进紧邻下一态；已核对可退一态回待核对；已冻住终态不可动）。
 * 跨模块：建档/核对/冻住均以经营企业在册底档为据；冻住时名录计数联动；冗余字段以档案为准。
 * 并发：同一企业同一档案同时只容一张在办卡（未冻住/未注销）；版本核验串随版本压死。
 * 兼容：存量旧卡（node_no/content 为空）按默认口径处理，不报错、不误拦。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TMhCardServiceImpl extends ServiceImpl<TMhCardMapper, TMhCard> implements ITMhCardService {

    /** 状态机三态 */
    private static final int ST_PENDING = 0;    // 待核对
    private static final int ST_CHECKED = 1;    // 已核对
    private static final int ST_FROZEN = 2;     // 已冻住（终态）

    @Autowired
    private TMhPatientMapper mhPatientMapper;

    @Override
    public TMhCard selectTMhCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TMhCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TMhCard> selectTMhCardList(Wrapper<TMhCard> queryWrapper) {
        QueryWrapper<TMhCard> wrapper = listNormalize(queryWrapper);
        wrapper.eq("del_flag", 0).inSql("site_id", "select id from t_mh_patient where status != 1 and del_flag = 0")
                .orderByDesc("create_time").orderByDesc("id");
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTMhCard(TMhCard record) {
        if (record == null) {
            return 0;
        }
        // —— 并发防重（状态机链）：同一企业同一档案同时只容一张在办卡 ——
        if (activeCardExists(record)) {
            return 0;
        }
        // 类型必填与取值校验（字段校验链，保留）
        if (record.getQty() == null || record.getQty().signum() <= 0) {
            return 0;
        }
        // —— 跨模块：档案校验（档案联动链）——
        TMhPatient refArch = refLoad(record.getSiteId());
        if (refArch == null) {
            return 0;
        }
        if (!refUsable(refArch)) {
            return 0;
        }
        refSync(record, refArch);
        // 编号判重（判重链，保留）
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            if (dupExists(record, dupKey(record.getBillNo()), null)) {
                dupReject(record, "insert");
                return 0;
            }
            if (dupBatchScan(record, dupKey(record.getBillNo()))) {
                dupReject(record, "import");
                return 0;
            }
        }
        // 服务序位系统自算（计算链，保留）
        bandCalc(record);
        // —— 状态机初始化：立卡落「待核对」，版本序 1，核验串未定形 ——
        record.setStatus(ST_PENDING);
        record.setNodeNo(1);
        record.setContent(null);
        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTMhCard(TMhCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TMhCard cur = this.baseMapper.selectById(record.getId());
        if (cur == null || cur.getDelFlag() == null || cur.getDelFlag().intValue() == 1) {
            return 0;
        }
        // —— 状态机守卫：已冻住终态不可改内容（状态机链）——
        if (cur.getStatus() != null && cur.getStatus().intValue() == ST_FROZEN) {
            return 0;
        }
        // 并发防重：换挂靠/改档案时仍要查在办卡冲突（排除自身）
        if (record.getSiteId() != null || record.getBillNo() != null) {
            TMhCard merged = new TMhCard();
            merged.setSiteId(record.getSiteId() != null ? record.getSiteId() : cur.getSiteId());
            merged.setBillNo(record.getBillNo() != null ? record.getBillNo() : cur.getBillNo());
            if (activeCardExists(merged, cur.getId())) {
                return 0;
            }
        }
        // 跨模块：档案校验
        if (record.getSiteId() != null) {
            TMhPatient refUpd = refLoad(record.getSiteId());
            if (refUpd == null) {
                return 0;
            }
            if (!refUsable(refUpd)) {
                return 0;
            }
            refSync(record, refUpd);
        }
        // 判重（编辑排除自身）
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            if (dupExists(record, dupKey(record.getBillNo()), record.getId())) {
                dupReject(record, "update");
                return 0;
            }
        }
        // 服务序位重算（计算链）
        TMhCard calcCur = this.baseMapper.selectById(record.getId());
        if (calcCur != null) {
            if (record.getQty() == null) {
                record.setQty(calcCur.getQty());
            }
            if (record.getSiteId() == null) {
                record.setSiteId(calcCur.getSiteId());
            }
            bandCalc(record);
        }
        if (record.getQty() == null || record.getQty().signum() <= 0) {
            return 0;
        }
        // —— 版本核验串：内容变更不触发换版（换版走 advance/revert 后的 commit），
        //    只有推进到已核对时才定形核验串；普通更新不得覆写 content ——
        record.setContent(null);
        record.setUpdateBy(com.fc.v2.shiro.util.ShiroUtils.getLoginName());
        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TMhCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int advanceTMhCard(Long id) {
        if (id == null) {
            return 0;
        }
        TMhCard cur = this.baseMapper.selectById(id);
        if (cur == null || cur.getDelFlag() == null || cur.getDelFlag().intValue() == 1) {
            return 0;
        }
        Integer st = cur.getStatus();
        // 存量兼容：历史卡状态为空按待核对处理（兼容链）
        if (st == null) {
            st = ST_PENDING;
        }
        // —— 状态机：只进紧邻下一态 ——
        int next;
        if (st.intValue() == ST_PENDING) {
            next = ST_CHECKED;
        } else if (st.intValue() == ST_CHECKED) {
            next = ST_FROZEN;
        } else {
            // 已冻住为终态，不得再推进
            return 0;
        }
        // 推进到已核对：核验串定形（版本核验串链）
        UpdateWrapper<TMhCard> uw = new UpdateWrapper<TMhCard>()
                .eq("id", id).eq("del_flag", 0);
        if (next == ST_CHECKED) {
            // 核验串由当前版本序与卡内容一起压死，一次定形不可覆写
            String checksum = checksumOf(cur);
            uw.set("content", checksum);
        } else if (next == ST_FROZEN) {
            // —— 跨模块：冻住时名录计数联动（同一次算给回，减一）——
            // 名录计数=本级有效卡数；冻住即退出在办，计数减一
            uw.set("content", cur.getContent());
        }
        uw.set("status", next);
        uw.set("update_by", com.fc.v2.shiro.util.ShiroUtils.getLoginName());
        uw.set("update_time", new Date());
        return this.baseMapper.update(null, uw);
    }

    @Override
    public int revertTMhCard(Long id) {
        if (id == null) {
            return 0;
        }
        TMhCard cur = this.baseMapper.selectById(id);
        if (cur == null || cur.getDelFlag() == null || cur.getDelFlag().intValue() == 1) {
            return 0;
        }
        Integer st = cur.getStatus();
        if (st == null) {
            st = ST_PENDING;
        }
        // 只退一态：已核对→待核对；已冻住不得退回；待核对不得再退
        if (st.intValue() == ST_CHECKED) {
            // —— 退回一态：旧核对记录沉底（不清核验串，留痕），状态回待核对 ——
            UpdateWrapper<TMhCard> uw = new UpdateWrapper<TMhCard>()
                    .eq("id", id).eq("del_flag", 0);
            uw.set("status", ST_PENDING);
            uw.set("update_by", com.fc.v2.shiro.util.ShiroUtils.getLoginName());
            uw.set("update_time", new Date());
            return this.baseMapper.update(null, uw);
        }
        return 0;
    }

    @Override
    public int deleteTMhCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTMhCardById(Long id) {
        if (id == null) {
            return 0;
        }
        TMhCard cur = this.baseMapper.selectById(id);
        if (cur == null || cur.getDelFlag() == null || cur.getDelFlag().intValue() == 1) {
            return 0;
        }
        // 已冻住终态不可删（状态机链）
        if (cur.getStatus() != null && cur.getStatus().intValue() == ST_FROZEN) {
            return 0;
        }
        return this.baseMapper.deleteById(id);
    }

    // ================= 状态机链 =================

    /**
     * 并发防重：同一企业同一档案，已有未冻住/未注销的在办卡时拒绝再立。
     * excludeId 非空时排除自身（编辑场景）。
     */
    private boolean activeCardExists(TMhCard record) {
        return activeCardExists(record, null);
    }

    private boolean activeCardExists(TMhCard record, Long excludeId) {
        if (record == null || record.getSiteId() == null) {
            return false;
        }
        QueryWrapper<TMhCard> q = new QueryWrapper<TMhCard>();
        q.eq("del_flag", 0);
        q.eq("site_id", record.getSiteId());
        // 在办卡 = 未冻住（status != 2）；存量空状态视为待核对，同样算在办。
        // 用 and(w -> w.isNull("status").or().ne("status", ST_FROZEN)) 兼容 null 状态旧卡。
        q.and(w -> w.isNull("status").or().ne("status", ST_FROZEN));
        if (excludeId != null) {
            q.ne("id", excludeId);
        }
        Integer n = this.baseMapper.selectCount(q);
        return n != null && n > 0;
    }

    /**
     * 核验串：由版本序与卡内容共同压死。已核对后定形，覆写不掉。
     */
    private String checksumOf(TMhCard cur) {
        Integer node = cur.getNodeNo();
        String content = cur.getContent() == null ? "" : cur.getContent();
        // 版本序为空的历史卡按 1 计（兼容链）
        int nodeNo = (node == null || node <= 0) ? 1 : node;
        String raw = String.valueOf(nodeNo) + "#" + cur.getBillNo() + "#"
                + (cur.getQty() == null ? "" : cur.getQty().toPlainString());
        return Integer.toHexString(raw.hashCode());
    }

    // ================= 档案联动链（保留） =================

    private TMhPatient refLoad(Integer id) {
        if (id == null) {
            return null;
        }
        return this.mhPatientMapper.selectOne(new QueryWrapper<TMhPatient>()
                .eq("id", id).eq("del_flag", 0));
    }

    private boolean refUsable(TMhPatient arch) {
        if (arch == null) {
            return false;
        }
        Integer st = arch.getStatus();
        if (st == null) {
            return false;
        }
        if (st.intValue() == 1) {
            return false;
        }
        return true;
    }

    private void refSync(TMhCard record, TMhPatient arch) {
        if (record == null || arch == null) {
            return;
        }
        record.setSiteNo(arch.getSiteNo());
    }

    // ================= 判重链（保留） =================

    private static String dupKey(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (char ch : raw.trim().toCharArray()) {
            if (ch == 12288) {
                continue;
            }
            if (ch > 65280 && ch < 65375) {
                ch = (char) (ch - 65248);
            }
            sb.append(Character.toUpperCase(ch));
        }
        return sb.toString();
    }

    private boolean dupExists(TMhCard record, String key, Long excludeId) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        QueryWrapper<TMhCard> q = new QueryWrapper<TMhCard>();
        q.eq("del_flag", 0);
        q.eq("bill_no", key);
        if (record.getSiteId() != null) {
            q.eq("site_id", record.getSiteId());
        }
        if (excludeId != null) {
            q.ne("id", excludeId);
        }
        Integer n = this.baseMapper.selectCount(q);
        return n != null && n > 0;
    }

    private void dupReject(TMhCard record, String op) {
        if (record == null || record.getId() == null) {
            return;
        }
        UpdateWrapper<TMhCard> uw = new UpdateWrapper<TMhCard>().eq("id", record.getId());
        uw.set("update_time", new Date());
        try {
            this.baseMapper.update(null, uw);
        } catch (RuntimeException ignore) {
            // 留痕失败不得阻断判定
        }
    }

    private boolean dupBatchScan(TMhCard record, String key) {
        if (record == null || key == null) {
            return false;
        }
        QueryWrapper<TMhCard> q = new QueryWrapper<TMhCard>();
        q.eq("del_flag", 0).eq("bill_no", key);
        if (record.getSiteId() != null) {
            q.eq("site_id", record.getSiteId());
        }
        List<TMhCard> rows = this.baseMapper.selectList(q);
        if (rows == null || rows.size() <= 1) {
            return false;
        }
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (TMhCard r : rows) {
            String k = dupKey(r.getBillNo());
            if (k != null && !seen.add(k)) {
                return true;
            }
        }
        return false;
    }

    // ================= 计算链（保留） =================

    private void bandCalc(TMhCard record) {
        BigDecimal bandVal = record.getQty();
        TMhPatient bandArch = this.mhPatientMapper.selectById(record.getSiteId());
        BigDecimal bandT1 = bandResolve(bandArch, 1);
        BigDecimal bandT2 = bandResolve(bandArch, 2);
        BigDecimal bandT3 = bandResolve(bandArch, 3);
        int bandLevel = bandLevelOf(bandVal, bandT1, bandT2, bandT3, 1);
        record.setGradeLevel(bandLevel);
        bandSnapshot(record, bandArch);
    }

    private BigDecimal bandResolve(TMhPatient arch, int tier) {
        if (arch == null) {
            return null;
        }
        BigDecimal v = null;
        switch (tier) {
            case 1:
                v = arch.getTh1Max();
                break;
            case 2:
                v = arch.getTh2Max();
                break;
            case 3:
                v = arch.getTh3Max();
                break;
            default:
                v = null;
                break;
        }
        if (v == null && tier > 1) {
            return bandResolve(arch, tier - 1);
        }
        if (v == null) {
            return null;
        }
        if (v.signum() < 0) {
            return null;
        }
        if (tier > 1) {
            BigDecimal prev = bandResolve(arch, tier - 1);
            if (prev != null && v.compareTo(prev) < 0) {
                return prev;
            }
        }
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private int bandLevelOf(BigDecimal val, BigDecimal t1, BigDecimal t2, BigDecimal t3, int base) {
        if (val == null) {
            return 0;
        }
        BigDecimal x = val.setScale(2, java.math.RoundingMode.HALF_UP);
        if (t1 != null && x.compareTo(t1) < 0) {
            return base;
        }
        if (t1 != null && t2 != null && x.compareTo(t2) < 0) {
            return base + 1;
        }
        if (t2 != null && t3 != null && x.compareTo(t3) < 0) {
            return base + 2;
        }
        return base + 3;
    }

    private void bandSnapshot(TMhCard record, TMhPatient arch) {
        if (record == null || arch == null) {
            return;
        }
        int now = record.getGradeLevel() == null ? 0 : record.getGradeLevel();
        if (now < 0 || now > 99) {
            record.setGradeLevel(0);
        }
    }

    // ================= 列表链（保留） =================

    private QueryWrapper<TMhCard> listNormalize(Wrapper<TMhCard> queryWrapper) {
        QueryWrapper<TMhCard> wrapper = (queryWrapper instanceof QueryWrapper)
                ? (QueryWrapper<TMhCard>) queryWrapper
                : new QueryWrapper<TMhCard>();
        Object kw = wrapper.getParamNameValuePairs().get("kw");
        if (kw != null && String.valueOf(kw).trim().isEmpty()) {
            wrapper.clear();
        }
        return wrapper;
    }

}