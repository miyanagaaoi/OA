package com.oa.platform.bootstrap;

import com.oa.common.config.OaProperties;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 不可篡改触发器初始化（doc/tech-design.md §5.5、AC-20）。
 *
 * <p>流程：读 {@code db/trigger/immutable-triggers.sql}（**无 DELIMITER**，语句以 {@code //} 分隔）
 * → 按 {@code //} 切分 → 过滤出含 {@code CREATE TRIGGER} 的块 → 查 {@code information_schema.TRIGGERS}
 * → 只创建**缺失**的触发器（幂等，重复启动不报错）。
 *
 * <p>与 Flyway 的分工：Flyway 迁移由主控维护的 {@code V1__schema.sql} 负责建表（已剔除触发器段），
 * 触发器由本类在启动时按需创建，从而绕开 Flyway 不识别 {@code DELIMITER} 的问题。
 *
 * <p>生产可通过 {@code oa.db.immutable-triggers-enabled=false} 关闭（例如由 DBA 统一维护 DDL 时）。
 */
@Component
public class ImmutableTriggerInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImmutableTriggerInitializer.class);

    private static final Pattern TRIGGER_NAME =
            Pattern.compile("(?i)CREATE\\s+TRIGGER\\s+`?([A-Za-z0-9_$]+)`?");

    private static final String EXISTS_SQL =
            "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE() AND TRIGGER_NAME = ?";

    private final DataSource dataSource;
    private final OaProperties properties;

    public ImmutableTriggerInitializer(DataSource dataSource, OaProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getDb().isImmutableTriggersEnabled()) {
            log.warn("不可篡改触发器初始化已关闭（oa.db.immutable-triggers-enabled=false）："
                    + "sys_log / flow_signature 的 UPDATE/DELETE 将失去数据库层兜底，请确认由 DBA 手工维护");
            return;
        }
        List<String> statements;
        try {
            statements = loadStatements(properties.getDb().getImmutableTriggersLocation());
        } catch (Exception ex) {
            log.error("读取不可篡改触发器 SQL 失败：{}", properties.getDb().getImmutableTriggersLocation(), ex);
            return;
        }
        int created = 0;
        for (String sql : statements) {
            try {
                if (ensureTrigger(sql)) {
                    created++;
                }
            } catch (SQLException ex) {
                log.error("创建不可篡改触发器失败：{}", firstLine(sql), ex);
            }
        }
        log.info("不可篡改触发器检查完成：脚本 {} 条，新创建 {} 条", statements.size(), created);
    }

    /**
     * 按「**单独成行的双斜杠**」切分，并只保留建触发器语句（注释块/空块自动跳过）。
     *
     * <p><b>为什么必须是行锚定切分（2026-10-02 实战事故）</b>：早期实现用 {@code content.split("//")}
     * （非锚定）。而本脚本头部的注释里出现了**字面量双斜杠**（原文写作「按 "//" 切分…」），
     * 于是第一个切分点落在注释内部，产出一个以引号开头的伪语句，它把紧随其后的
     * {@code CREATE TRIGGER trg_sys_log_no_update} **一并吞掉** → 该语句语法错误 → 触发器永不创建，
     * 而其余 3 条落在后续块中正常创建。后果是 **AC-20 对 {@code sys_log} 的改保护在数据库层完全不存在**
     * （{@code UPDATE sys_log} 实测可成功），且启动日志只留一行 ERROR，属静默失效。
     *
     * <p>因此：① 切分改为行锚定（注释里出现任何 {@code //} 都不会误切）；
     * ② 增加**条数自检**：脚本中 {@code CREATE TRIGGER} 的出现次数必须与解析出的语句数一致，
     * 不一致即判为脚本损坏并抛出（宁可启动失败，也不能静默少建保护）。
     */
    List<String> loadStatements(String location) throws Exception {
        Resource resource = new DefaultResourceLoader().getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("触发器脚本不存在：" + location);
        }
        String content;
        try (var inputStream = resource.getInputStream()) {
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> statements = new ArrayList<>();
        int declared = 0;
        // 行锚定：仅匹配「整行只有双斜杠（可含空白）」作为分隔符
        for (String chunk : content.split("(?m)^\\s*//\\s*$")) {
            String sql = stripLeadingComments(chunk);
            if (sql.isBlank()) {
                continue;
            }
            String upper = sql.toUpperCase(Locale.ROOT);
            // 只在**剥离注释后**的语句文本里计数：注释里出现 "CREATE TRIGGER" 字样不得被算作声明
            declared += countOccurrences(upper, "CREATE TRIGGER");
            if (upper.contains("CREATE TRIGGER")) {
                statements.add(sql);
            }
        }
        // 条数自检：脚本里声明几次 CREATE TRIGGER，就必须解析出几条语句
        if (declared != statements.size()) {
            throw new IllegalStateException(String.format(
                    "触发器脚本解析异常：脚本声明 %d 条 CREATE TRIGGER，实际解析出 %d 条（切分或注释损坏）。"
                            + "文件=%s；这会导致部分不可篡改保护静默缺失（AC-20），已拒绝启动。",
                    declared, statements.size(), location));
        }
        if (statements.isEmpty()) {
            throw new IllegalStateException("触发器脚本未解析出任何语句：" + location);
        }
        return statements;
    }

    /** 统计子串出现次数（用于触发器条数自检）。 */
    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    /** @return 本次是否新建（已存在返回 false） */
    boolean ensureTrigger(String sql) throws SQLException {
        String name = triggerName(sql);
        if (name == null) {
            log.warn("无法从语句中解析触发器名，跳过：{}", firstLine(sql));
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            if (exists(connection, name)) {
                log.debug("触发器已存在，跳过：{}", name);
                return false;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
            log.info("已创建不可篡改触发器：{}", name);
            return true;
        }
    }

    private boolean exists(Connection connection, String name) throws SQLException {
        try (PreparedStatement prepared = connection.prepareStatement(EXISTS_SQL)) {
            prepared.setString(1, name);
            try (ResultSet resultSet = prepared.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private static String triggerName(String sql) {
        Matcher matcher = TRIGGER_NAME.matcher(sql);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 去掉块首的空行与 {@code --}/{@code #} 注释（块内的注释保留，触发器正文可含注释）。 */
    private static String stripLeadingComments(String chunk) {
        StringBuilder builder = new StringBuilder();
        for (String line : chunk.split("\r?\n")) {
            String trimmed = line.trim();
            if (builder.length() == 0 && (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("#"))) {
                continue;
            }
            builder.append(line).append('\n');
        }
        return builder.toString().trim();
    }

    private static String firstLine(String sql) {
        int index = sql.indexOf('\n');
        return index < 0 ? sql : sql.substring(0, index);
    }
}
