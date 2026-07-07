package com.mikle.syncup.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mikle.syncup.service.UserTeamService;
import com.mikle.syncup.model.domain.UserTeam;
import com.mikle.syncup.mapper.UserTeamMapper;
import org.springframework.stereotype.Service;

/**
 * 用户队伍服务实现类
 */
@Service
public class UserTeamServiceImpl extends ServiceImpl<UserTeamMapper, UserTeam>
        implements UserTeamService {

}




