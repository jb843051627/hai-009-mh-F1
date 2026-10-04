-- mh 智慧医疗门诊管理系统 -- schema (hai-009)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：hai_009

CREATE TABLE IF NOT EXISTS t_mh_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '就诊档案卡号',
  node_no int DEFAULT NULL COMMENT '本卡版本次序',
  site_id int DEFAULT NULL COMMENT '所属底档',
  site_no varchar(64) DEFAULT NULL COMMENT '所属底档代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本卡年就诊次数',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本年度新增就诊次数',
  content varchar(255) DEFAULT NULL COMMENT '校验串（随版本次序走）',
  grade_level int DEFAULT NULL COMMENT '就诊次数折到的随访保障层级',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已压死',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='就诊档案卡';

CREATE TABLE IF NOT EXISTS t_mh_inpat_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '住院处置单号',
  stage int DEFAULT NULL COMMENT '当前处置段 0..3（入院/检查/治疗/出院封卷）',
  status int DEFAULT NULL COMMENT '单子落定 0未起 1在办 2已封存',
  content varchar(255) DEFAULT NULL COMMENT '单上记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='住院处置单';

CREATE TABLE IF NOT EXISTS t_mh_med_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '特殊用药签核单号',
  node_no int DEFAULT NULL COMMENT '当前所在道 0..2（开方提/药剂科核/医务科批）',
  sign_mode int DEFAULT NULL COMMENT '同口并印办法 0任一人 1两名点齐',
  need_count int DEFAULT NULL COMMENT '本道应落印数',
  sign_count int DEFAULT NULL COMMENT '本道已落印数',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已打回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='特殊用药签核单';

CREATE TABLE IF NOT EXISTS t_mh_patient (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '患者底档代号',
  site_name varchar(128) DEFAULT NULL COMMENT '底档名称',
  site_type varchar(32) DEFAULT NULL COMMENT '底档类别(普通/慢病/特需)',
  road_name varchar(128) DEFAULT NULL COMMENT '所属行政区划(省—市—区—街道)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '单档年就诊次数一档上限',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档上限',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档上限',
  status int DEFAULT NULL COMMENT '底档情形 0在用 1已注销',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='患者底档';

CREATE TABLE IF NOT EXISTS t_mh_reg_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '号源转号事项号',
  queue_key varchar(64) DEFAULT NULL COMMENT '所投候补队列代号（同科室/同专家共用一档）',
  doctor_no varchar(64) DEFAULT NULL COMMENT '出诊医生代号（转号前查休诊用）',
  release_no varchar(64) DEFAULT NULL COMMENT '最早定下的放号日批次号（放号即定，撤号/改用途/重投均不许覆写）',
  due_at datetime DEFAULT NULL COMMENT '放号截止那一天最后一刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '单条提前转号的自然日数（离截止剩几日该浮出）',
  content varchar(255) DEFAULT NULL COMMENT '号源事由与承接科室记要',
  status int DEFAULT NULL COMMENT '条目情形 0待转 1已转 2转不出去 3挂起',
  wait_id bigint DEFAULT NULL COMMENT '实际承接的候补排队人（谁接的）',
  transferred_at datetime DEFAULT NULL COMMENT '真转出时刻（转号落下时一并记下，落定后不许覆写）',
  suspend_reason varchar(255) DEFAULT NULL COMMENT '挂起原由（医生休诊/科室停摆/候补整队列撤了）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间（即最早定的放号日，不许覆写）',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_reg_scan (del_flag, status, queue_key, due_at),
  KEY idx_reg_release (release_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='号源到期转号事项条';

CREATE TABLE IF NOT EXISTS t_mh_tria_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '分诊判定线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '判定线名',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '危重档界值（含）',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '紧急档界值（含）',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '普通档界值（含）',
  eff_start datetime DEFAULT NULL COMMENT '启用之日',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含)',
  priority int DEFAULT NULL COMMENT '判定线顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '判定线情形 0现行 1已让位',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='急诊分诊判定线';

CREATE TABLE IF NOT EXISTS t_mh_visit_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '门诊就诊数据上传批次号',
  row_no int DEFAULT NULL COMMENT '原表行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被校验出的数据项代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行就诊费用',
  status int DEFAULT NULL COMMENT '行落地情形 0待核 1已入库 2退回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门诊就诊记录导入核对行';

-- 初始档案数据（id=1 启用 / id=2 停用）
-- 初始档案数据：id=0 在用、id=1 已注销
-- 年就诊次数上限 3/10/30 次：等于上限算高一档。
INSERT IGNORE INTO t_mh_patient (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'PT00', '临江市东湖街慢病随访组（在用）', '慢病', '临江省—临江市—东湖区—东湖街道', 3.00, 10.00, 30.00, 0, 0, 'seed', NOW()),
       (1, 'PT01', '临江市城南普通门诊组（已注销）', '普通', '临江省—临江市—城南区—望湖街道', 3.00, 10.00, 30.00, 1, 0, 'seed', NOW());

CREATE TABLE IF NOT EXISTS t_mh_reg_wait (
  id bigint NOT NULL COMMENT '主键',
  queue_key varchar(64) DEFAULT NULL COMMENT '所在候补队列代号',
  waiter_no varchar(64) DEFAULT NULL COMMENT '候补排队人代号',
  waiter_name varchar(128) DEFAULT NULL COMMENT '候补人姓名（留痕用）',
  enqueue_at datetime DEFAULT NULL COMMENT '挂上候补队列的时刻（排号先后以此为准）',
  is_ordinary int DEFAULT '0' COMMENT '是否普通挂号 1普通挂号（不许插队） 0候补',
  status int DEFAULT NULL COMMENT '排队情形 0在排 1已接号 2已撤',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_wait_pick (del_flag, status, queue_key, enqueue_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='号源候补排队人';

CREATE TABLE IF NOT EXISTS t_mh_reg_window (
  id bigint NOT NULL COMMENT '主键',
  window_code varchar(64) DEFAULT NULL COMMENT '扫号窗口代号',
  window_name varchar(128) DEFAULT NULL COMMENT '窗口名（值班划的）',
  window_type int DEFAULT '0' COMMENT '窗口档别 0日常 1流感高峰单开档',
  weekday_mask varchar(16) DEFAULT NULL COMMENT '开扫星期，7 位 0/1，依次周一..周日（如 1111100）',
  open_at varchar(8) DEFAULT NULL COMMENT '开窗时刻 HH:mm:ss',
  close_at varchar(8) DEFAULT NULL COMMENT '关窗时刻 HH:mm:ss（含）',
  eff_start date DEFAULT NULL COMMENT '档别启用之日',
  eff_end date DEFAULT NULL COMMENT '档别交棒之日(不含)，空为长期',
  priority int DEFAULT '0' COMMENT '档别撞期时的说话顺位（数值大者先判定）',
  status int DEFAULT '0' COMMENT '窗口情形 0现行 1已停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='值班划的扫号窗口';

CREATE TABLE IF NOT EXISTS t_mh_holiday (
  id bigint NOT NULL COMMENT '主键',
  holiday_date date NOT NULL COMMENT '法定节假日之日（整段不扫）',
  holiday_name varchar(128) DEFAULT NULL COMMENT '节假日名（留痕用）',
  status int DEFAULT '0' COMMENT '情形 0有效 1已作废',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_holiday_date (holiday_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='法定节假日';

CREATE TABLE IF NOT EXISTS t_mh_reg_closure (
  id bigint NOT NULL COMMENT '主键',
  closure_type int DEFAULT NULL COMMENT '停摆类别 1医生休诊 2科室停摆 3候补整队列撤了',
  doctor_no varchar(64) DEFAULT NULL COMMENT '休诊医生代号（医生休诊时填）',
  queue_key varchar(64) DEFAULT NULL COMMENT '受影响候补队列代号（科室停摆/撤队列时填）',
  eff_start datetime DEFAULT NULL COMMENT '停摆起（含）',
  eff_end datetime DEFAULT NULL COMMENT '停摆止（不含），空为至今未恢复',
  status int DEFAULT '0' COMMENT '情形 0在停 1已恢复',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_closure_find (del_flag, status, queue_key, doctor_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='转号前停摆（休诊/停摆/撤队列）';

CREATE TABLE IF NOT EXISTS t_mh_reg_log (
  id bigint NOT NULL COMMENT '主键',
  task_id bigint DEFAULT NULL COMMENT '号源事项条id（哪一个号）',
  item_no varchar(64) DEFAULT NULL COMMENT '号源转号事项号（快照留痕）',
  release_no varchar(64) DEFAULT NULL COMMENT '最早定下的放号日批次号（快照留痕）',
  queue_key varchar(64) DEFAULT NULL COMMENT '候补队列代号',
  wait_id bigint DEFAULT NULL COMMENT '承接的候补排队人id（谁接的）',
  waiter_no varchar(64) DEFAULT NULL COMMENT '承接人代号（快照留痕）',
  window_code varchar(64) DEFAULT NULL COMMENT '落账时所在扫号窗口档',
  run_batch varchar(48) DEFAULT NULL COMMENT '本轮扫号批次号（同一屏同一批）',
  action int DEFAULT NULL COMMENT '动作 1转出 2挂起 3撤号 4改用途',
  reason varchar(255) DEFAULT NULL COMMENT '原由（挂起/撤号/改用途留痕）',
  acted_at datetime DEFAULT NULL COMMENT '动作落账时刻（转出即真转出时刻）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_log_task (task_id, action, acted_at),
  KEY idx_log_batch (run_batch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='号源转号台账（谁接的/何时接的/撤号留痕）';

INSERT IGNORE INTO t_mh_reg_window (id, window_code, window_name, window_type, weekday_mask, open_at, close_at, eff_start, eff_end, priority, status, del_flag, create_by, create_time)
VALUES (0, 'DAY', '白天日常档', 0, '1111100', '08:00:00', '18:00:00', NULL, NULL, 0, 0, 0, 'seed', NOW());
