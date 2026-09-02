package com.mikle.syncup.assembler;

import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.vo.UserSearchResultVO;
import com.mikle.syncup.model.vo.UserVO;
import com.mikle.syncup.service.TagService;
import org.springframework.stereotype.Component;

/**
 * 用户对象转换器。所有对外字段均采用显式白名单映射，避免意外暴露密码等敏感信息。
 */
@Component
public class UserAssembler {

    private final TagService tagService;

    public UserAssembler(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * 构造仅供服务端内部使用的当前登录用户，不携带密码和逻辑删除字段。
     */
    public User toAuthenticatedUser(User source) {
        if (source == null) {
            return null;
        }
        User target = new User();
        target.setId(source.getId());
        target.setUsername(source.getUsername());
        target.setUserAccount(source.getUserAccount());
        target.setAvatarUrl(source.getAvatarUrl());
        target.setGender(source.getGender());
        target.setPhone(source.getPhone());
        target.setEmail(source.getEmail());
        target.setCity(source.getCity());
        target.setTagIds(source.getTagIds());
        target.setProfile(source.getProfile());
        target.setUserStatus(source.getUserStatus());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        target.setLastActiveTime(source.getLastActiveTime());
        target.setUserRole(source.getUserRole());
        return target;
    }

    /**
     * 转换为当前用户本人可见的资料。
     */
    public UserVO toCurrentUserVO(User source) {
        if (source == null) {
            return null;
        }
        UserVO target = new UserVO();
        target.setId(source.getId());
        target.setUsername(source.getUsername());
        target.setUserAccount(source.getUserAccount());
        target.setAvatarUrl(source.getAvatarUrl());
        target.setGender(source.getGender());
        target.setPhone(source.getPhone());
        target.setEmail(source.getEmail());
        target.setCity(source.getCity());
        target.setTagIds(tagService.parseTagIds(source.getTagIds()));
        target.setTagNames(tagService.toDisplayTagNames(source.getTagIds()));
        target.setProfile(source.getProfile());
        target.setUserStatus(source.getUserStatus());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        target.setLastActiveTime(source.getLastActiveTime());
        target.setUserRole(source.getUserRole());
        return target;
    }

    /**
     * 转换为其他用户可见的公开资料，不包含账号和联系方式。
     */
    public UserSearchResultVO toPublicUserVO(User source) {
        if (source == null) {
            return null;
        }
        UserSearchResultVO target = new UserSearchResultVO();
        target.setId(source.getId());
        target.setUsername(source.getUsername());
        target.setAvatarUrl(source.getAvatarUrl());
        target.setGender(source.getGender());
        target.setCity(source.getCity());
        target.setTagNames(tagService.toDisplayTagNames(source.getTagIds()));
        target.setProfile(source.getProfile());
        target.setCreateTime(source.getCreateTime());
        target.setLastActiveTime(source.getLastActiveTime());
        return target;
    }
}
