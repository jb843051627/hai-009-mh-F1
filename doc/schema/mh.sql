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
  due_at datetime DEFAULT NULL COMMENT '放号截止那一天最后一刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '单条提前转号的自然日数',
  content varchar(255) DEFAULT NULL COMMENT '号源事由与承接科室记要',
  status int DEFAULT NULL COMMENT '条目情形 0待转 1已转 2转不出去 3已挂起 4已撤号 5已改用途',
  release_at datetime DEFAULT NULL COMMENT '最早定的放号日（落定后永不覆盖）',
  trans_at datetime DEFAULT NULL COMMENT '真转出时刻（与 status=已转 同生，缺一不算转成）',
  suspend_reason varchar(255) DEFAULT NULL COMMENT '挂起原由（医生休诊/科室停摆/候补队列撤了）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_item_no (item_no),
  KEY idx_status_due (status, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='号源到期转号事项条';

CREATE TABLE IF NOT EXISTS t_mh_wait_line (
  id bigint NOT NULL COMMENT '主键',
  reg_id bigint NOT NULL COMMENT '所候号源事项（t_mh_reg_task.id）',
  card_id bigint DEFAULT NULL COMMENT '候补人就诊卡',
  queue_type int DEFAULT 0 COMMENT '候补来路 0候补排队 1普通挂号（普通挂号不许插队）',
  enqueue_at datetime NOT NULL COMMENT '挂上候补队列的时刻（排号唯一次序）',
  join_day date DEFAULT NULL COMMENT '当天二次进队列判重日（同档同日只认头一回）',
  queue_status int DEFAULT 0 COMMENT '排队情形 0在排 1已接号 2已撤队',
  picked_at datetime DEFAULT NULL COMMENT '接号时刻',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_reg_enq (reg_id, queue_status, enqueue_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='号源候补排队条';

CREATE TABLE IF NOT EXISTS t_mh_scan_window (
  id bigint NOT NULL COMMENT '主键',
  win_code varchar(64) NOT NULL COMMENT '窗口档代号',
  win_name varchar(128) DEFAULT NULL COMMENT '窗口档名（白天档/流感高峰档）',
  win_kind int DEFAULT 0 COMMENT '档别 0日常白天档 1流感高峰专档（专档不走日常窗口）',
  day_from time NOT NULL COMMENT '档内每日起（含）',
  day_to time NOT NULL COMMENT '档内每日止（不含）',
  date_from date DEFAULT NULL COMMENT '专档适用首日（含）；日常档可空',
  date_to date DEFAULT NULL COMMENT '专档适用末日（含）；日常档可空',
  status int DEFAULT 0 COMMENT '窗口情形 0现行 1已停',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_win_code (win_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='扫号值班窗口档';

CREATE TABLE IF NOT EXISTS t_mh_holiday (
  id bigint NOT NULL COMMENT '主键',
  holi_date date NOT NULL COMMENT '法定节假日当日（整段不扫）',
  holi_name varchar(128) DEFAULT NULL COMMENT '节名',
  holi_year int DEFAULT NULL COMMENT '年度',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_holi_date (holi_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='法定节假日历';

CREATE TABLE IF NOT EXISTS t_mh_reg_handover (
  id bigint NOT NULL COMMENT '主键',
  reg_id bigint NOT NULL COMMENT '转号事项（t_mh_reg_task.id）',
  item_no varchar(64) DEFAULT NULL COMMENT '事项号（冗余留痕，事项删了也查得到）',
  act_type int NOT NULL COMMENT '交接动作 1转成 2挂起 3撤号 4改用途',
  win_code varchar(64) DEFAULT NULL COMMENT '落定所在扫号窗口档（同档同号只转一次据此判）',
  from_wait_id bigint DEFAULT NULL COMMENT '接号候补条（转成时落）',
  card_id bigint DEFAULT NULL COMMENT '接号人（谁接了）',
  act_at datetime NOT NULL COMMENT '动作时刻（什么时候接的/撤的）',
  reason varchar(255) DEFAULT NULL COMMENT '挂起原由/撤号或改用途说明',
  round_no varchar(64) DEFAULT NULL COMMENT '所属扫号轮次号',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_reg_act (reg_id, act_type, act_at),
  KEY idx_round (round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='号源转号交接留痕';

CREATE TABLE IF NOT EXISTS t_mh_scan_round (
  id bigint NOT NULL COMMENT '主键',
  round_no varchar(64) NOT NULL COMMENT '轮次号',
  win_code varchar(64) DEFAULT NULL COMMENT '本轮所开窗口档',
  scan_at datetime DEFAULT NULL COMMENT '本轮点算时刻',
  due_count int DEFAULT 0 COMMENT '本轮到期条数（窗口外点算为 0，不动号）',
  wait_count int DEFAULT 0 COMMENT '本轮结账：待转几条',
  done_count int DEFAULT 0 COMMENT '本轮结账：已转几条',
  hold_count int DEFAULT 0 COMMENT '本轮结账：挂起几条',
  run_status int DEFAULT 0 COMMENT '本轮情形 0无活 1已执行 2出错',
  err_msg varchar(500) DEFAULT NULL COMMENT '出错信息（无活与出错绝不能混为一谈）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_round_no (round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='扫号轮次结账台';

-- 初始档案数据（id=1 启用 / id=2 停用）
-- 初始档案数据：id=0 在用、id=1 已注销
-- 年就诊次数上限 3/10/30 次：等于上限算高一档。
INSERT IGNORE INTO t_mh_patient (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'PT00', '临江市东湖街慢病随访组（在用）', '慢病', '临江省—临江市—东湖区—东湖街道', 3.00, 10.00, 30.00, 0, 0, 'seed', NOW()),
       (1, 'PT01', '临江市城南普通门诊组（已注销）', '普通', '临江省—临江市—城南区—望湖街道', 3.00, 10.00, 30.00, 1, 0, 'seed', NOW());

-- 日常白天扫号档：08:00（含）–18:00（不含），夜间与法定节假日整段不扫。
INSERT IGNORE INTO t_mh_scan_window (id, win_code, win_name, win_kind, day_from, day_to, date_from, date_to, status, del_flag, create_by, create_time)
VALUES (0, 'DAY', '白天日常档', 0, '08:00:00', '18:00:00', NULL, NULL, 0, 0, 'seed', NOW());
