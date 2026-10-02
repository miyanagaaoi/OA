package com.oa.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.identity.domain.IdentityEnums.Category;
import com.oa.identity.domain.IdentityEnums.LeaderType;
import com.oa.identity.domain.IdentityEnums.OrgStatus;
import com.oa.identity.domain.IdentityEnums.OrgType;
import com.oa.identity.domain.IdentityEnums.PrimaryFlag;
import com.oa.identity.domain.IdentityEnums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 枚举字典（**纯逻辑单测**）：中文标签 ↔ code 的映射集中在一处，且与
 * doc/import-spec.md §3、doc/enums.md §14、DDL 注释逐条一致（施工要求第 8 条）。
 */
class IdentityEnumsTest {

    @Test
    @DisplayName("组织类型：集团/公司/部门/科室 ↔ group/company/dept/section，层级 1/2/3/4")
    void orgTypeMapping() {
        assertThat(OrgType.parse("集团")).isEqualTo(OrgType.GROUP);
        assertThat(OrgType.parse("公司")).isEqualTo(OrgType.COMPANY);
        assertThat(OrgType.parse("部门")).isEqualTo(OrgType.DEPT);
        assertThat(OrgType.parse("科室")).isEqualTo(OrgType.SECTION);
        assertThat(OrgType.parse("GROUP")).isEqualTo(OrgType.GROUP);
        assertThat(OrgType.GROUP.depth()).isEqualTo(1);
        assertThat(OrgType.COMPANY.depth()).isEqualTo(2);
        assertThat(OrgType.DEPT.depth()).isEqualTo(3);
        assertThat(OrgType.SECTION.depth()).isEqualTo(4);
        assertThatThrownBy(() -> OrgType.parse("分公司")).isInstanceOf(BizException.class)
                .hasMessageContaining("未知组织类型");
    }

    @Test
    @DisplayName("状态：组织 启用/停用；人员 在职/停用/离职")
    void statusMapping() {
        assertThat(OrgStatus.parse("启用")).isEqualTo(OrgStatus.ACTIVE);
        assertThat(OrgStatus.parse("停用")).isEqualTo(OrgStatus.DISABLED);
        assertThat(UserStatus.parse("在职")).isEqualTo(UserStatus.ACTIVE);
        assertThat(UserStatus.parse("离职")).isEqualTo(UserStatus.RESIGNED);
        assertThat(UserStatus.parse("disabled")).isEqualTo(UserStatus.DISABLED);
        assertThat(UserStatus.RESIGNED.label()).isEqualTo("离职");
    }

    @Test
    @DisplayName("负责人类型与主岗标识：正职/副职、是/否")
    void leaderAndPrimaryMapping() {
        assertThat(LeaderType.parse("正职")).isEqualTo(LeaderType.PRIMARY);
        assertThat(LeaderType.parse("副职")).isEqualTo(LeaderType.DEPUTY);
        assertThat(LeaderType.parse("primary")).isEqualTo(LeaderType.PRIMARY);
        assertThat(PrimaryFlag.parse("是")).isEqualTo(PrimaryFlag.PRIMARY);
        assertThat(PrimaryFlag.parse("否")).isEqualTo(PrimaryFlag.NON_PRIMARY);
        assertThat(PrimaryFlag.parse("1").value()).isEqualTo(1);
        assertThat(PrimaryFlag.of(true)).isEqualTo(PrimaryFlag.PRIMARY);
        assertThatThrownBy(() -> PrimaryFlag.parse("maybe")).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("事项类别五值（业务线）：经营/经济/行政/人力/投资 ↔ business/economy/admin/hr/invest")
    void categoryMapping() {
        assertThat(Category.parse("经营")).isEqualTo(Category.BUSINESS);
        assertThat(Category.parse("经济")).isEqualTo(Category.ECONOMY);
        assertThat(Category.parse("行政")).isEqualTo(Category.ADMIN);
        assertThat(Category.parse("人力")).isEqualTo(Category.HR);
        assertThat(Category.parse("投资")).isEqualTo(Category.INVEST);
        assertThat(Category.parse("business")).isEqualTo(Category.BUSINESS);
        assertThat(Category.labels()).containsExactly("经营", "经济", "行政", "人力", "投资");
        // 旧码 operate 作废并迁移为 business（enums.md §14）
        assertThat(Category.ofCode("operate")).isEqualTo(Category.BUSINESS);
        assertThat(Category.parse("OPERATE")).isEqualTo(Category.BUSINESS);
        assertThatThrownBy(() -> Category.parse("财务")).isInstanceOf(BizException.class)
                .hasMessageContaining("未知事项类别");
        assertThatThrownBy(() -> Category.parse(null)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("未知 code 不抛异常（历史脏数据容错）：ofCode 返回 null；labelOf 原样返回 code")
    void unknownCodeTolerance() {
        assertThat(OrgType.ofCode("unknown")).isNull();
        assertThat(OrgStatus.ofCode(null)).isNull();
        assertThat(IdentityEnums.labelOf(OrgType.GROUP)).isEqualTo("集团");
        assertThat(IdentityEnums.labelOf((OrgType) null)).isNull();
        assertThat(IdentityEnums.labelOf(Category.INVEST)).isEqualTo("投资");
    }
}
