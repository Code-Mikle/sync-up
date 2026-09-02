package com.mikle.syncup.service;

import com.mikle.syncup.model.domain.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mikle.syncup.model.vo.UserLoginVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mikle.syncup.model.vo.UserSearchResultVO;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户服务
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     */
    UserLoginVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 更新用户信息
     */
    int updateUser(User user, User loginUser);

    /**
     * 用户退出
     */
    int userLogout(HttpServletRequest request);

    /**
     * 获取当前登录用户信息
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 根据标签搜索用户
     * @param tagIds 标准标签 id，满足任一标签即可
     * @param pageNum 当前页码，从 1 开始
     * @param pageSize 每页数量
     * @param excludeUserId 不应出现在结果中的用户 id，可为空
     */
    Page<UserSearchResultVO> searchUsersByTags(List<Long> tagIds,
                                               long pageNum,
                                               long pageSize,
                                               Long excludeUserId);

    /**
     * Keyword search for public user information.
     * @param keywords      keywords split from user input
     * @param pageNum       current page number
     * @param pageSize      page size
     * @param excludeUserId user id that should not appear in results
     * @return paged public search results
     */
    Page<UserSearchResultVO> searchUsersByKeywords(List<String> keywords, long pageNum, long pageSize, Long excludeUserId);

    /**
     * 是否为管理员
     */
    boolean isAdmin(HttpServletRequest request);

    /**
     * 是否为管理员
     */
    boolean isAdmin(User loginUser);

}
