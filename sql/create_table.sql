create database if not exists sync_up_db;

use sync_up_db;

-- 用户表
create table user
(
    username     varchar(256) null comment '用户昵称',
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256) null comment '账号',
    avatarUrl    varchar(1024) null comment '用户头像',
    gender       tinyint null comment '性别',
    userPassword varchar(512) not null comment '密码',
    phone        varchar(128) null comment '电话',
    email        varchar(512) null comment '邮箱',
    userStatus   int      default 0 not null comment '状态 0 - 正常',
    createTime   datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete     tinyint  default 0 not null comment '是否删除',
    userRole     int      default 0 not null comment '用户角色 0 - 普通用户 1 - 管理员',
    planetCode   varchar(512) null comment '星球编号',
    tags         varchar(1024) null comment '标签 json 列表',
    profile      varchar(1024) null comment '个人简介 / 自我介绍'
) comment '用户';

-- 队伍表
create table team
(
    id          bigint auto_increment comment 'id' primary key,
    name        varchar(256) not null comment '队伍名称',
    description varchar(1024) null comment '描述',
    maxNum      int      default 1 not null comment '最大人数',
    expireTime  datetime null comment '过期时间',
    activityType varchar(64) null comment '活动类型',
    city        varchar(64) null comment '城市',
    district    varchar(64) null comment '区域',
    startTime   datetime null comment '活动开始时间',
    durationMinutes int null comment '预计时长，单位分钟',
    budgetPerPerson decimal(10, 2) null comment '人均预算',
    skillLevel  varchar(32) null comment '水平要求',
    userId      bigint null comment '用户id（队长 id）',
    status      int      default 0 not null comment '0 - 公开，1 - 私有，2 - 加密',
    password    varchar(512) null comment '密码',
    createTime  datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete    tinyint  default 0 not null comment '是否删除'
) comment '队伍';

create index idx_team_userId on team (userId);
create index idx_team_search on team (status, city, activityType, startTime);

-- 用户队伍关系表
create table user_team
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint null comment '用户id',
    teamId     bigint null comment '队伍id',
    joinTime   datetime null comment '加入时间',
    createTime datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete   tinyint  default 0 not null comment '是否删除'
) comment '用户队伍关系';

create unique index uk_user_team_userId_teamId on user_team (userId, teamId);
create index idx_user_team_teamId on user_team (teamId);

-- AI 队伍草稿表
create table ai_team_draft
(
    id              bigint auto_increment comment 'id' primary key,
    draftId         varchar(64) not null comment 'AI 草稿公开 id',
    sessionId       varchar(64) null comment 'AI 对话会话 id',
    userId          bigint not null comment '草稿所属用户 id',
    name            varchar(256) not null comment '队伍名称',
    description     varchar(1024) null comment '描述',
    maxNum          int not null comment '最大人数',
    activityType    varchar(64) null comment '活动类型',
    city            varchar(64) null comment '城市',
    district        varchar(64) null comment '区域',
    startTime       datetime null comment '活动开始时间',
    durationMinutes int null comment '预计时长，单位分钟',
    budgetPerPerson decimal(10, 2) null comment '人均预算',
    skillLevel      varchar(32) null comment '水平要求',
    status          tinyint default 0 not null comment '0 - 待确认，1 - 已确认，2 - 已过期',
    confirmedTeamId bigint null comment '确认后创建的队伍 id',
    confirmedAt     datetime null comment '确认时间',
    expiresAt       datetime not null comment '草稿过期时间',
    createTime      datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete        tinyint default 0 not null comment '是否删除'
) comment 'AI 队伍草稿';

create unique index uk_ai_team_draft_draftId on ai_team_draft (draftId);
create index idx_ai_team_draft_user_status on ai_team_draft (userId, status, expiresAt);

-- AI 工具调用审计表
create table ai_tool_call_log
(
    id               bigint auto_increment comment 'id' primary key,
    sessionId        varchar(64) null comment 'AI 对话会话 id',
    userId           bigint null comment '用户 id',
    actionType       varchar(64) not null comment '动作类型',
    toolName         varchar(64) not null comment '工具名称',
    status           varchar(32) not null comment 'success / failed',
    argumentsSummary varchar(1024) null comment '脱敏参数摘要',
    resultSummary    varchar(1024) null comment '结果摘要',
    errorMessage     varchar(1024) null comment '错误摘要',
    durationMs       bigint null comment '耗时毫秒',
    relatedDraftId   varchar(64) null comment '关联草稿 id',
    relatedTeamId    bigint null comment '关联队伍 id',
    createTime       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete         tinyint default 0 not null comment '是否删除'
) comment 'AI 工具调用审计';

create index idx_ai_tool_call_log_user_time on ai_tool_call_log (userId, createTime);
create index idx_ai_tool_call_log_session on ai_tool_call_log (sessionId);
create index idx_ai_tool_call_log_action_status on ai_tool_call_log (actionType, status);

-- AI 用户画像表
create table ai_user_profile
(
    id            bigint auto_increment comment 'id' primary key,
    userId        bigint not null comment '用户 id',
    profileJson   text not null comment '结构化画像 JSON',
    sourceText    varchar(1024) null comment '画像来源文本，已做最小化脱敏',
    modelVersion  varchar(64) not null comment '提取模型或规则版本',
    status        tinyint default 1 not null comment '1 - 已确认',
    confirmedAt   datetime null comment '用户确认时间',
    createTime    datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete      tinyint default 0 not null comment '是否删除'
) comment 'AI 用户结构化画像';

create unique index uk_ai_user_profile_userId on ai_user_profile (userId);
create index idx_ai_user_profile_updateTime on ai_user_profile (updateTime);

-- AI 用户画像提取任务表
create table ai_profile_extraction_task
(
    id             bigint auto_increment comment 'id' primary key,
    taskId         varchar(64) not null comment '画像提取任务公开 id',
    userId         bigint not null comment '用户 id',
    sourceText     varchar(1024) not null comment '来源文本，已做最小化脱敏',
    extractionJson text not null comment '提取出的结构化画像 JSON',
    status         tinyint default 1 not null comment '0 - 待处理，1 - 已提取，2 - 已确认，3 - 已拒绝，4 - 失败',
    retryCount     int default 0 not null comment '重试次数',
    nextRetryAt    datetime null comment '下次重试时间',
    lastError      varchar(1024) null comment '最后一次错误',
    modelVersion   varchar(64) not null comment '提取模型或规则版本',
    createTime     datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete       tinyint default 0 not null comment '是否删除'
) comment 'AI 用户画像提取任务';

create unique index uk_ai_profile_task_taskId on ai_profile_extraction_task (taskId);
create index idx_ai_profile_task_user_status on ai_profile_extraction_task (userId, status, createTime);

-- AI 短期会话记忆表
create table ai_chat_memory
(
    id           bigint auto_increment comment 'id' primary key,
    memoryId     varchar(160) not null comment '会话记忆 id，userId:sessionId',
    userId       bigint not null comment '用户 id',
    sessionId    varchar(64) not null comment 'AI 对话会话 id',
    messagesJson mediumtext not null comment 'LangChain4j ChatMessage JSON',
    messageCount int default 0 not null comment '消息数量',
    expireAt     datetime not null comment '过期时间',
    createTime   datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete     tinyint default 0 not null comment '是否删除'
) comment 'AI 短期会话记忆';

create unique index uk_ai_chat_memory_memoryId on ai_chat_memory (memoryId);
create index idx_ai_chat_memory_user_time on ai_chat_memory (userId, updateTime);
create index idx_ai_chat_memory_expireAt on ai_chat_memory (expireAt);

-- 标签表（可以不创建，因为标签字段已经放到用户表中）
create table tag
(
    id         bigint auto_increment comment 'id' primary key,
    tagName    varchar(256) null comment '标签名称',
    userId     bigint null comment '用户 id',
    parentId   bigint null comment '父标签 id',
    isParent   tinyint null comment '0 - 不是，1 - 父标签',
    createTime datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete   tinyint  default 0 not null comment '是否删除',
    constraint uniIdx_tagName
        unique (tagName)
) comment '标签';

create index idx_userId on tag (userId);
