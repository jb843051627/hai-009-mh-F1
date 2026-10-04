-- H2（MODE=MySQL）内存库：仅转号机制单元测试用，列与 doc/schema/mh.sql 六张表对齐。
CREATE TABLE t_mh_reg_task (
  id bigint PRIMARY KEY,
  item_no varchar(64),
  due_at datetime,
  amount decimal(12,2),
  content varchar(255),
  status int,
  release_at datetime,
  trans_at datetime,
  suspend_reason varchar(255),
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);

CREATE TABLE t_mh_wait_line (
  id bigint PRIMARY KEY,
  reg_id bigint NOT NULL,
  card_id bigint,
  queue_type int DEFAULT 0,
  enqueue_at datetime NOT NULL,
  join_day date,
  queue_status int DEFAULT 0,
  picked_at datetime,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);

CREATE TABLE t_mh_scan_window (
  id bigint PRIMARY KEY,
  win_code varchar(64) NOT NULL,
  win_name varchar(128),
  win_kind int DEFAULT 0,
  day_from datetime NOT NULL,
  day_to datetime NOT NULL,
  date_from date,
  date_to date,
  status int DEFAULT 0,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);

CREATE TABLE t_mh_holiday (
  id bigint PRIMARY KEY,
  holi_date date NOT NULL,
  holi_name varchar(128),
  holi_year int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);

CREATE TABLE t_mh_reg_handover (
  id bigint PRIMARY KEY,
  reg_id bigint NOT NULL,
  item_no varchar(64),
  act_type int NOT NULL,
  win_code varchar(64),
  from_wait_id bigint,
  card_id bigint,
  act_at datetime NOT NULL,
  reason varchar(255),
  round_no varchar(64),
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);

CREATE TABLE t_mh_scan_round (
  id bigint PRIMARY KEY,
  round_no varchar(64) NOT NULL,
  win_code varchar(64),
  scan_at datetime,
  due_count int DEFAULT 0,
  wait_count int DEFAULT 0,
  done_count int DEFAULT 0,
  hold_count int DEFAULT 0,
  run_status int DEFAULT 0,
  err_msg varchar(500),
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);
