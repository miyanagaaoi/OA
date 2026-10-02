package com.oa.identity.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.oa.identity.api.dto.DirectoryDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 水印策略（**纯逻辑单测**）：REQ-USER-004 / AC-44 / PRD §6.8 / §13.2 ——
 * 内容为「姓名 + 工号」、透明度 5%–8%、旋转 -24°、不遮挡按钮与表单值。
 */
class WatermarkPolicyTest {

    @Test
    @DisplayName("水印载荷：姓名 + 工号（不含他人姓名）；工号缺失显式标注数据缺口")
    void payload() {
        DirectoryDtos.WatermarkView payload = WatermarkPolicy.payload(7L, "王甲", "A0001");
        assertThat(payload.text()).isEqualTo("王甲 A0001");
        assertThat(payload.employeeNoMissing()).isFalse();
        assertThat(payload.userId()).isEqualTo(7L);
        assertThat(payload.policy().contentLabel()).isEqualTo("姓名+工号");

        DirectoryDtos.WatermarkView missing = WatermarkPolicy.payload(8L, "李乙", null);
        assertThat(missing.text()).isEqualTo("李乙");
        assertThat(missing.employeeNoMissing()).isTrue();

        DirectoryDtos.WatermarkView blank = WatermarkPolicy.payload(9L, " 孙七 ", "  ");
        assertThat(blank.text()).isEqualTo("孙七");
        assertThat(blank.employeeNoMissing()).isTrue();
    }

    @Test
    @DisplayName("策略常量：透明度 5%–8%、旋转 -24°、模板 {name} {employeeNo}")
    void policyConstants() {
        DirectoryDtos.WatermarkPolicyView policy = WatermarkPolicy.view();
        assertThat(policy.enabled()).isTrue();
        assertThat(policy.contentLabel()).isEqualTo("姓名+工号");
        assertThat(policy.template()).isEqualTo("{name} {employeeNo}");
        assertThat(policy.minOpacityPercent()).isEqualTo(5);
        assertThat(policy.maxOpacityPercent()).isEqualTo(8);
        assertThat(policy.rotationDegrees()).isEqualTo(-24);
        assertThat(policy.avoidInteractiveElements()).isTrue();
    }

    @Test
    @DisplayName("文案拼接：缺姓名/缺工号都不产生多余空格")
    void textFallbacks() {
        assertThat(WatermarkPolicy.text("王甲", "A0001")).isEqualTo("王甲 A0001");
        assertThat(WatermarkPolicy.text(null, "A0001")).isEqualTo("A0001");
        assertThat(WatermarkPolicy.text("王甲", "")).isEqualTo("王甲");
        assertThat(WatermarkPolicy.text(null, null)).isEmpty();
    }
}
