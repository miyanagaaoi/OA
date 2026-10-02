-- 回归夹具：注释里含**字面量双斜杠**的触发器脚本（不能出现整行只有双斜杠的行）
-- 历史事故：脚本头注释写了「按 "//" 切分…」，而解析器用非锚定的 content.split("//")，
-- 切分点落在注释内部，产出的伪语句把紧随其后的 CREATE TRIGGER 一并吞掉，
-- 导致 sys_log 的改保护在数据库层静默缺失（AC-20）。本夹具用于锁死该回归。
--
-- 下面这行故意包含字面量：// 这里不是分隔符 //
CREATE TRIGGER trg_sys_log_no_update BEFORE UPDATE ON sys_log
FOR EACH ROW BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_log is append-only';
END
//
CREATE TRIGGER trg_sys_log_no_delete BEFORE DELETE ON sys_log
FOR EACH ROW BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_log is append-only';
END
