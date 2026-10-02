package com.oa.identity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.identity.api.dto.PositionDtos;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.domain.SysUser;
import com.oa.identity.domain.SysUserPosition;
import com.oa.identity.infra.SysUserMapper;
import com.oa.identity.infra.SysUserPositionMapper;
import com.oa.identity.infra.row.PositionRow;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 主岗切换单测（施工要求第 5 条）：
 * {@code PUT /users/{id}/positions/{positionId}} 必须做到
 * <ol>
 *   <li>设为主岗时**同事务**把该人的旧主岗置 0（复用 {@code PositionPolicy#demotionsFor}）；</li>
 *   <li>回填 {@code sys_user.org_id}/{@code position}（import-spec §2.2 第 ④ 步 / T-12）；</li>
 *   <li>处理的是**目标行本人**的旧主岗，别的用户的岗位不受影响；</li>
 *   <li>岗位名支持规范名 {@code postName} 与旧别名 {@code position}；</li>
 *   <li>把唯一主岗置否 → 409（避免「有岗位但无主岗」）；越权改他人岗位 → 404。</li>
 * </ol>
 *
 * <p>纯逻辑单测：Mapper 用 Mockito 替身，不连 DB / 不起 Spring 容器（与既有单测同一风格）。
 */
class PositionServiceTest {

    private SysUserPositionMapper positionMapper;
    private SysUserMapper userMapper;
    private OrgService orgService;
    private PositionService service;

    @BeforeEach
    void setUp() {
        positionMapper = mock(SysUserPositionMapper.class);
        userMapper = mock(SysUserMapper.class);
        orgService = mock(OrgService.class);
        service = new PositionService(positionMapper, userMapper, orgService);
        CurrentUser principal = CurrentUser.of(99L, "admin01", "系统管理员", "A0099", 1L, 1L,
                Set.of("admin"), Set.of(), false);
        DataScopeContext.set(DataScopeContext.builder().principal(principal).build());
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private static SysUser user() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setAccount("liyi0002");
        user.setName("李乙");
        user.setOrgId(100L);
        user.setCompanyId(12L);
        user.setPosition("专员");
        return user;
    }

    private static SysUserPosition position(long id, long orgId, boolean primary) {
        SysUserPosition row = new SysUserPosition();
        row.setId(id);
        row.setUserId(7L);
        row.setOrgId(orgId);
        row.setIsPrimary(primary ? 1 : 0);
        row.setPosition("专员");
        return row;
    }

    private static PositionRow positionRow(long id, long orgId, boolean primary, String position) {
        PositionRow row = new PositionRow();
        row.setId(id);
        row.setUserId(7L);
        row.setOrgId(orgId);
        row.setOrgName("部门" + orgId);
        row.setPosition(position);
        row.setIsPrimary(primary ? 1 : 0);
        return row;
    }

    @Test
    @DisplayName("设为主岗：旧主岗置 0 且回填 sys_user.org_id/position（T-12）")
    void promoteDemotesOldPrimaryAndBackfillsUser() {
        SysUserPosition oldPrimary = position(1L, 100L, true);
        SysUserPosition target = position(2L, 200L, false);
        when(userMapper.selectUserById(7L)).thenReturn(user());
        when(positionMapper.selectById(2L)).thenReturn(target);
        when(positionMapper.selectByUserId(7L)).thenReturn(List.of(oldPrimary, target));
        SysOrg targetOrg = new SysOrg();
        targetOrg.setId(200L);
        targetOrg.setPath("/1/12/200/");
        when(orgService.findOrg(200L)).thenReturn(targetOrg);
        when(positionMapper.selectRowsByUserId(7L))
                .thenReturn(List.of(positionRow(2L, 200L, true, "部门经理")));

        PositionDtos.PositionView view = service.update(7L, 2L,
                new PositionDtos.PositionUpdateRequest("部门经理", null, true, "调入"));

        // ① 旧主岗置 0（同事务），目标行置 1
        verify(positionMapper).updatePrimary(1L, 0);
        ArgumentCaptor<Integer> flag = ArgumentCaptor.forClass(Integer.class);
        verify(positionMapper).updatePosition(eq(2L), eq("部门经理"), eq("调入"), flag.capture());
        assertThat(flag.getValue()).isEqualTo(1);
        // ② 主岗回填 sys_user
        ArgumentCaptor<SysUser> updated = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateUserProfile(updated.capture());
        assertThat(updated.getValue().getOrgId()).isEqualTo(200L);
        assertThat(updated.getValue().getPosition()).isEqualTo("部门经理");
        assertThat(updated.getValue().getUpdatedBy()).isEqualTo(99L);
        // ③ 出参取自重新查询的行
        assertThat(view.id()).isEqualTo(2L);
        assertThat(view.isPrimary()).isTrue();
        // 没有多余的「提升其它岗位」动作
        verify(positionMapper, never()).updatePrimary(eq(1L), eq(1));
    }

    @Test
    @DisplayName("规范名 postName 优先于旧别名 position（前端历史实现兼容）")
    void postNameWinsOverLegacyAlias() {
        SysUserPosition target = position(2L, 200L, false);
        when(userMapper.selectUserById(7L)).thenReturn(user());
        when(positionMapper.selectById(2L)).thenReturn(target);
        when(positionMapper.selectByUserId(7L)).thenReturn(List.of(target));
        SysOrg targetOrg = new SysOrg();
        targetOrg.setId(200L);
        when(orgService.findOrg(200L)).thenReturn(targetOrg);
        when(positionMapper.selectRowsByUserId(7L)).thenReturn(List.of(positionRow(2L, 200L, true, "规范名")));

        service.update(7L, 2L, new PositionDtos.PositionUpdateRequest("规范名", "旧别名", true, null));

        verify(positionMapper).updatePosition(eq(2L), eq("规范名"), isNull(), eq(1));
    }

    @Test
    @DisplayName("只传 isPrimary=false 且仅一个岗位：409，且不写库（避免无主岗）")
    void demotingOnlyPrimaryIsRejected() {
        SysUserPosition only = position(1L, 100L, true);
        when(userMapper.selectUserById(7L)).thenReturn(user());
        when(positionMapper.selectById(1L)).thenReturn(only);
        when(positionMapper.selectByUserId(7L)).thenReturn(List.of(only));

        assertThatThrownBy(() -> service.update(7L, 1L,
                new PositionDtos.PositionUpdateRequest(null, null, false, null)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("唯一的主岗");

        verify(positionMapper, never()).updatePosition(anyLong(), any(), any(), any());
        verify(userMapper, never()).updateUserProfile(any());
    }

    @Test
    @DisplayName("多岗位时置否主岗：复用兜底纯函数，把剩余最早岗位提升为主岗")
    void demotingPrimaryPromotesEarliestRemaining() {
        SysUserPosition primary = position(1L, 100L, true);
        SysUserPosition other = position(5L, 200L, false);
        when(userMapper.selectUserById(7L)).thenReturn(user());
        when(positionMapper.selectById(1L)).thenReturn(primary);
        when(positionMapper.selectByUserId(7L)).thenReturn(List.of(primary, other));
        when(positionMapper.selectRowsByUserId(7L)).thenReturn(List.of(positionRow(1L, 100L, false, "专员")));

        service.update(7L, 1L, new PositionDtos.PositionUpdateRequest(null, null, false, null));

        verify(positionMapper).updatePosition(1L, null, null, 0);
        verify(positionMapper).updatePrimary(5L, 1);
        // 置否不产生 sys_user 回填
        verify(userMapper, never()).updateUserProfile(any());
    }

    @Test
    @DisplayName("越权：岗位不属于该用户 → 404")
    void positionOfAnotherUserIsNotFound() {
        SysUserPosition foreign = position(9L, 100L, false);
        foreign.setUserId(8L);
        when(userMapper.selectUserById(7L)).thenReturn(user());
        when(positionMapper.selectById(9L)).thenReturn(foreign);

        assertThatThrownBy(() -> service.update(7L, 9L,
                new PositionDtos.PositionUpdateRequest("专员", null, true, null)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(positionMapper, never()).updatePosition(anyLong(), any(), any(), any());
        verify(positionMapper, times(0)).selectByUserId(anyLong());
    }
}
