-- H2（MODE=MySQL）转号机制测试库；最小列集，无 COMMENT/ENGINE 子句。
DROP ALL OBJECTS;

CREATE TABLE t_mh_reg_task (
  id BIGINT NOT NULL PRIMARY KEY,
  item_no VARCHAR(64),
  queue_key VARCHAR(64),
  doctor_no VARCHAR(64),
  release_no VARCHAR(64),
  due_at DATETIME,
  amount DECIMAL(12,2),
  content VARCHAR(255),
  status INT,
  wait_id BIGINT,
  transferred_at DATETIME,
  suspend_reason VARCHAR(255),
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500)
);

CREATE TABLE t_mh_reg_wait (
  id BIGINT NOT NULL PRIMARY KEY,
  queue_key VARCHAR(64),
  waiter_no VARCHAR(64),
  waiter_name VARCHAR(128),
  enqueue_at DATETIME,
  is_ordinary INT DEFAULT 0,
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500)
);

CREATE TABLE t_mh_reg_window (
  id BIGINT NOT NULL PRIMARY KEY,
  window_code VARCHAR(64),
  window_name VARCHAR(128),
  window_type INT DEFAULT 0,
  weekday_mask VARCHAR(16),
  open_at VARCHAR(8),
  close_at VARCHAR(8),
  eff_start DATE,
  eff_end DATE,
  priority INT DEFAULT 0,
  status INT DEFAULT 0,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500)
);

CREATE TABLE t_mh_holiday (
  id BIGINT NOT NULL PRIMARY KEY,
  holiday_date DATE NOT NULL,
  holiday_name VARCHAR(128),
  status INT DEFAULT 0,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500)
);

CREATE TABLE t_mh_reg_closure (
  id BIGINT NOT NULL PRIMARY KEY,
  closure_type INT,
  doctor_no VARCHAR(64),
  queue_key VARCHAR(64),
  eff_start DATETIME,
  eff_end DATETIME,
  status INT DEFAULT 0,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500)
);

CREATE TABLE t_mh_reg_log (
  id BIGINT NOT NULL PRIMARY KEY,
  task_id BIGINT,
  item_no VARCHAR(64),
  release_no VARCHAR(64),
  queue_key VARCHAR(64),
  wait_id BIGINT,
  waiter_no VARCHAR(64),
  window_code VARCHAR(64),
  run_batch VARCHAR(48),
  action INT,
  reason VARCHAR(255),
  acted_at DATETIME,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500)
);
