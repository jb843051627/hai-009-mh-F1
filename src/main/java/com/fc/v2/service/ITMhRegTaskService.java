package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TMhRegTask;
import com.fc.v2.model.custom.reg.RegRunResult;

/**
 * TMhRegTask 业务层处理接口
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMhRegTaskService {

    /** selectTMhRegTaskById */
    TMhRegTask selectTMhRegTaskById(Long id);

    /** 到期已浮出、仍在待转的号（状态 0 且 at 已到"截止日-提前日数"） */
    List<TMhRegTask> listDue(Date at);

    /**
     * 扫一轮号。
     * 窗口外/本轮无活/处理出错三态在回执里明说，互不混淆；
     * 待转/已转/挂起三个数只算这一轮，且扫完后同一次 GROUP BY 点算给回。
     */
    RegRunResult runOnce(Date at);

    /** 定时任务入口（无参，墙钟取当下） */
    RegRunResult runOnce();

    /**
     * 撤号：过了期限还转不出去的安置办法之一。留痕，
     * 且最早定的放号日（release_no/create_time）与真转出时刻（transferred_at）不许覆写。
     * @return 落账条数 1=撤成 0=没撤成（状态不对/号不存在）
     */
    int cancelTask(Long id, String reason);

    /**
     * 改用途：过了期限还转不出去的另一种安置。留痕，同样不许覆写放号日/转出时刻。
     * @return 落账条数
     */
    int repurposeTask(Long id, String newContent, String reason);

}
