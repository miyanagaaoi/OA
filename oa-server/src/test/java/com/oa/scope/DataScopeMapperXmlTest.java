package com.oa.scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mapper XML 装载校验（无 DB / 无 Spring 容器）：证明
 * <ol>
 *   <li>{@code mapper/identity/SysUserMapper.xml} 能被 MyBatis-Plus 正常解析（列名/resultMap/命名空间无笔误）；</li>
 *   <li>{@code @dataScope} 标记在 MyBatis 解析后**仍然保留在 SQL 文本里**（它是 SQL 注释文本，不是 XML 注释），
 *       因此 {@code DataScopeInterceptor} 一定能看到并替换它。</li>
 * </ol>
 */
class DataScopeMapperXmlTest {

    private static final String RESOURCE = "mapper/identity/SysUserMapper.xml";
    private static final String STATEMENT = "com.oa.identity.infra.SysUserMapper.selectDirectory";

    @Test
    @DisplayName("通讯录查询的 Mapper XML 可解析，且保留 @dataScope 标记")
    void mapperXmlParsesAndKeepsMarker() throws Exception {
        Configuration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream inputStream = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(inputStream, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }

        assertThat(configuration.hasStatement(STATEMENT)).isTrue();
        String sql = configuration.getMappedStatement(STATEMENT).getBoundSql(null).getSql();
        assertThat(sql).contains("/* @dataScope(table=sys_user, alias=u) */");
        assertThat(sql).contains("FROM sys_user u");
        assertThat(sql).doesNotContain("<!--");
    }
}
