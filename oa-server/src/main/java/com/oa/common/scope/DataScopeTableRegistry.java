package com.oa.common.scope;

import com.oa.common.config.OaProperties;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

/**
 * 受控表登记表：合并「配置白名单（{@code oa.scope.tables}）」与「实体注解（{@link DataScopeTable}）」，
 * 供 {@link DataScopeInterceptor} 判断某张表该织入哪种口径、以及某条 SQL 是否属于受控表的裸查询。
 */
@Component
public class DataScopeTableRegistry {

    private static final Logger log = LoggerFactory.getLogger(DataScopeTableRegistry.class);

    /** 注解扫描范围（实体位于各领域的 {@code domain} 包）。 */
    private static final String SCAN_BASE_PACKAGE = "com.oa";

    /** 表名条目。 */
    public record Entry(String table, String alias, DataScopeKind kind) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final List<String> exemptStatementIds;
    private final boolean enforceUnmarkedSelect;
    private volatile Pattern scopedTablePattern = Pattern.compile("(?!)");

    public DataScopeTableRegistry(OaProperties properties) {
        OaProperties.Scope scope = properties.getScope();
        this.exemptStatementIds = scope.getExemptStatementIds() == null ? List.of() : List.copyOf(scope.getExemptStatementIds());
        this.enforceUnmarkedSelect = scope.isEnforceUnmarkedSelect();
        if (scope.getTables() != null) {
            for (OaProperties.ScopeTable item : scope.getTables()) {
                register(item.getTable(), item.getAlias(), parseKind(item.getKind()));
            }
        }
        scanAnnotatedEntities();
        rebuildPattern();
        log.info("数据域受控表登记完成：{}，裸查询拦截={}", entries.keySet(), enforceUnmarkedSelect);
    }

    /** 登记/覆盖一张受控表。 */
    public void register(String table, String alias, DataScopeKind kind) {
        if (table == null || table.isBlank()) {
            return;
        }
        String key = table.trim().toLowerCase(Locale.ROOT);
        entries.put(key, new Entry(key, alias == null || alias.isBlank() ? "i" : alias.trim(), kind == null ? DataScopeKind.INSTANCE : kind));
    }

    public Optional<Entry> find(String table) {
        if (table == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(table.trim().toLowerCase(Locale.ROOT)));
    }

    public boolean isScoped(String table) {
        return find(table).isPresent();
    }

    public Set<String> scopedTables() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(entries.keySet()));
    }

    /** 命中任一受控表的正则（用于裸查询检测，词边界匹配，避免误伤注释/字符串字面量之外的标识符）。 */
    public Pattern scopedTablePattern() {
        return scopedTablePattern;
    }

    public boolean isEnforceUnmarkedSelect() {
        return enforceUnmarkedSelect;
    }

    /** 是否为免拦截的 MappedStatement（身份/会话/字典等天然自限查询）。 */
    public boolean isExempt(String statementId) {
        if (statementId == null) {
            return false;
        }
        for (String prefix : exemptStatementIds) {
            if (statementId.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void rebuildPattern() {
        if (entries.isEmpty()) {
            scopedTablePattern = Pattern.compile("(?!)");
            return;
        }
        StringBuilder builder = new StringBuilder("\\b(?:");
        boolean first = true;
        for (String table : entries.keySet()) {
            if (!first) {
                builder.append('|');
            }
            builder.append(Pattern.quote(table));
            first = false;
        }
        builder.append(")\\b");
        scopedTablePattern = Pattern.compile(builder.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static DataScopeKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return DataScopeKind.INSTANCE;
        }
        try {
            return DataScopeKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("未知的数据域 kind={}，按 INSTANCE 处理", kind);
            return DataScopeKind.INSTANCE;
        }
    }

    /** 扫描 {@link DataScopeTable} 注解（实体声明优先于配置白名单）。 */
    private void scanAnnotatedEntities() {
        try {
            ClassPathScanningCandidateComponentProvider provider =
                    new ClassPathScanningCandidateComponentProvider(false);
            provider.addIncludeFilter(new AnnotationTypeFilter(DataScopeTable.class));
            for (BeanDefinition definition : provider.findCandidateComponents(SCAN_BASE_PACKAGE)) {
                String className = definition.getBeanClassName();
                if (className == null) {
                    continue;
                }
                Class<?> clazz = Class.forName(className, false, DataScopeTableRegistry.class.getClassLoader());
                DataScopeTable annotation = clazz.getAnnotation(DataScopeTable.class);
                if (annotation != null) {
                    register(annotation.table(), annotation.alias(), annotation.kind());
                }
            }
        } catch (Exception ex) {
            // 扫描失败不影响主流程：配置白名单已兜底
            log.warn("扫描 @DataScopeTable 注解失败，仅使用配置白名单：{}", ex.getMessage());
        }
    }
}
