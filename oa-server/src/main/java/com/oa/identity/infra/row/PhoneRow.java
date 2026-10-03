package com.oa.identity.infra.row;

/**
 * 手机号密文行（{@code sys_user.id} + {@code sys_user.phone}）。
 *
 * <p>用途：1.7 的一次性迁移（明文 → 密文）与密钥轮换（旧 keyId → 活动 keyId）需要
 * **只取这两列**遍历全表；不返回其它字段可把迁移期的数据暴露面压到最小。
 */
public class PhoneRow {

    private Long id;

    private String phone;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
