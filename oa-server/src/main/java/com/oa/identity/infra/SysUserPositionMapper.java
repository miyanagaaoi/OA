package com.oa.identity.infra;

import com.oa.identity.domain.SysUserPosition;
import com.oa.identity.infra.row.PositionRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 岗位任职 Mapper（{@code sys_user_position}）。
 *
 * <p>同样**不使用 {@code BaseMapper}**（{@code sys_user_position} 已登记为受控表，
 * BaseMapper 注入语句缺 {@code @dataScope} 标记会被 fail-closed 拒绝）。
 * 所有 SELECT 在 XML 中带 {@code /* @dataScope(table=sys_user_position, alias=up) *}{@code /}。
 */
@Mapper
public interface SysUserPositionMapper {

    /** 岗位列表（含组织名/路径），供 {@code GET /users/{id}/positions} 展示。 */
    List<PositionRow> selectRowsByUserId(@Param("userId") Long userId);

    /** 岗位实体列表（供 {@code PositionPolicy} 纯函数判定主岗唯一）。 */
    List<SysUserPosition> selectByUserId(@Param("userId") Long userId);

    /** 按 id 取（校验归属关系时必须确认该岗位属于该用户，防越权改他人岗位）。 */
    SysUserPosition selectById(@Param("id") Long id);

    /** 按 {@code (user_id, org_id)} 取（唯一键 {@code uk_user_org}）。 */
    SysUserPosition selectByUserAndOrg(@Param("userId") Long userId, @Param("orgId") Long orgId);

    /** 该组织下的岗位数（组织停用的影响面提示）。 */
    int countByOrgId(@Param("orgId") Long orgId);

    int insertPosition(SysUserPosition position);

    /** 只改主岗标记（旧主岗置 0 / 新主岗置 1 都用它）。 */
    int updatePrimary(@Param("id") Long id, @Param("isPrimary") Integer isPrimary);

    /** 更新岗位名/备注/主岗标记。 */
    int updatePosition(@Param("id") Long id, @Param("position") String position,
                       @Param("remark") String remark, @Param("isPrimary") Integer isPrimary);

    int deleteById(@Param("id") Long id);
}
