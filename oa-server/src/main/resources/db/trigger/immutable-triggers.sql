-- ============================================================================
-- 不可篡改触发器（sys_log / flow_signature：拒绝 UPDATE 与 DELETE，AC-20）
-- ----------------------------------------------------------------------------
-- 生成器: tools/build-flyway-migrations.js sha256=1d187b7628f8
-- 确定性: 无墙钟时间戳/随机量；同一输入重复生成逐字节一致（Flyway checksum 稳定）。
-- 请勿手工编辑本文件：改 oa-deploy/sql 或文档后重跑生成器。
-- 来源: oa-deploy/sql/01-schema.sql 的 DELIMITER 段 sha256=6f1fa43778ba
-- 本文件不是 Flyway 迁移：由 com.oa.platform.bootstrap.ImmutableTriggerInitializer 在启动时读取，
-- 按「单独成行的双斜杠」切分为独立语句，逐条检查 information_schema.TRIGGERS 后 **幂等创建缺失项**。
-- 注意：本文件的注释里**不要出现字面量的双斜杠**（历史上曾因此误切、吞掉一条 CREATE TRIGGER，
-- 导致 sys_log 的改保护静默缺失）；解析器已改为行锚定切分并自带条数自检。
-- 原因：Flyway 的 MySQL 解析器不识别 mysql 客户端的 DELIMITER 语法。
-- ============================================================================

-- MySQL 8.0：用触发器强制拒绝修改与删除
CREATE TRIGGER trg_sys_log_no_update BEFORE UPDATE ON sys_log
FOR EACH ROW BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_log is append-only';
END
//
CREATE TRIGGER trg_sys_log_no_delete BEFORE DELETE ON sys_log
FOR EACH ROW BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_log is append-only';
END
//
CREATE TRIGGER trg_flow_signature_no_update BEFORE UPDATE ON flow_signature
FOR EACH ROW BEGIN
  -- 仅允许写入验签结果（二期 CA 验签回写），其余字段一律禁止修改
  IF NEW.sign_image  <=> OLD.sign_image
     AND NEW.hash      <=> OLD.hash
     AND NEW.user_id   <=> OLD.user_id
     AND NEW.signed_at <=> OLD.signed_at THEN
    SET NEW.verify_result = NEW.verify_result;  -- 放行
  ELSE
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'flow_signature is append-only';
  END IF;
END
//
CREATE TRIGGER trg_flow_signature_no_delete BEFORE DELETE ON flow_signature
FOR EACH ROW BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'flow_signature is append-only';
END
