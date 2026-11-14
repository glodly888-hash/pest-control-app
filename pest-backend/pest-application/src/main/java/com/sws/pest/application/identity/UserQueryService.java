package com.sws.pest.application.identity;

import com.sws.pest.domain.identity.AppUser;
import com.sws.pest.domain.identity.AppUserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 当前员工查询用例（getMe）。
 * 从 JWT 解析出的 userId 回查员工主体，返回基础信息 + 权限快照。
 */
@Service
public class UserQueryService {

    private final AppUserRepository appUserRepository;

    public UserQueryService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * 按用户 ID 查询员工。首期角色/权限/区域暂为占位，后续从 user_role / role_permission / user_scope join。
     */
    public AppUser findById(UUID userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }
}
