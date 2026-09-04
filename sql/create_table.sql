create database if not exists sync_up_db;

create database if not exists sync_up_test;
use sync_up_test;

use sync_up_db;

TRUNCATE TABLE user;
TRUNCATE TABLE team;
TRUNCATE TABLE user_team;
# TRUNCATE TABLE ai_tool_call_log;
# TRUNCATE TABLE ai_team_embedding;
TRUNCATE TABLE ai_team_draft;

TRUNCATE TABLE tag;


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
    city         varchar(64) null comment '常驻城市',
    userStatus   int      default 0 not null comment '状态 0 - 正常',
    createTime   datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    lastActiveTime datetime null comment '最近活跃时间',
    isDelete     tinyint  default 0 not null comment '是否删除',
    userRole     int      default 0 not null comment '用户角色 0 - 普通用户 1 - 管理员',
    tagIds       json null comment '标准活动标签 id JSON 数组',
    profile      varchar(1024) null comment '个人简介 / 自我介绍',
    unique key uk_user_userAccount (userAccount)
) comment '用户';

-- 队伍表
create table team
(
    id          bigint auto_increment comment 'id' primary key,
    name        varchar(256) not null comment '队伍名称',
    description varchar(1024) null comment '描述',
    activityCategory int default 9 null comment '活动大类',
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
    isDelete    tinyint  default 0 not null comment '是否删除',
    key idx_team_userId (userId),
    key idx_team_search (status, city, activityCategory, startTime)
) comment '队伍';

-- 用户队伍关系表
create table user_team
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint null comment '用户id',
    teamId     bigint null comment '队伍id',
    joinTime   datetime null comment '加入时间',
    createTime datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete   tinyint  default 0 not null comment '是否删除',
    unique key uk_user_team_userId_teamId (userId, teamId),
    key idx_user_team_teamId (teamId)
) comment '用户队伍关系';

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
    activityCategory int default 9 null comment '活动大类',
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
    isDelete        tinyint default 0 not null comment '是否删除',
    unique key uk_ai_team_draft_draftId (draftId),
    key idx_ai_team_draft_user_status (userId, status, expiresAt)
) comment 'AI 队伍草稿';

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
    isDelete         tinyint default 0 not null comment '是否删除',
    key idx_ai_tool_call_log_user_time (userId, createTime),
    key idx_ai_tool_call_log_session (sessionId),
    key idx_ai_tool_call_log_action_status (actionType, status)
) comment 'AI 工具调用审计';

-- AI 内部用户画像表
create table ai_user_profile
(
    id                          bigint auto_increment comment 'id' primary key,
    userId                      bigint not null comment '用户 id',
    activityPreferenceText      text not null comment '兴趣与活动偏好',
    socialPersonalityText       text not null comment '社交与性格倾向',
    partnerPreferenceText       text not null comment '搭子匹配偏好',
    activityConstraintHabitText text not null comment '活动约束与习惯',
    aiInteractionPreferenceText text not null comment 'AI 交互偏好',
    profileText                 text not null comment '完整五段式内部画像',
    matchProfileText            text not null comment '用于匹配的前四段画像',
    interactionProfileText      text not null comment '仅用于 AI 交流方式的第五段画像',
    profileVersion              int not null comment '画像版本号',
    evidenceDigest              char(64) not null comment '画像证据 SHA-256',
    model                       varchar(128) not null comment '生成模型',
    promptVersion               varchar(64) not null comment '画像 Prompt 版本',
    status                      varchar(32) not null comment 'ACTIVE / REBUILD_REQUIRED',
    generatedAt                 datetime not null comment '生成时间',
    createTime                  datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime                  datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete                    tinyint default 0 not null comment '是否删除',
    unique key uk_ai_user_profile_userId (userId),
    key idx_ai_user_profile_status_updateTime (status, updateTime)
) comment 'AI 内部用户文本画像';

-- AI 用户画像向量表
create table ai_user_profile_embedding
(
    id               bigint auto_increment comment 'id' primary key,
    userId           bigint not null comment '用户 id',
    profileVersion   int not null comment '对应画像版本号',
    matchTextHash    char(64) not null comment '匹配画像文本 SHA-256',
    embeddingModel   varchar(128) not null comment 'Embedding 模型',
    dimensions       int not null comment '向量维度',
    vectorJson       mediumtext not null comment '归一化后的 float 向量 JSON',
    status           tinyint default 1 not null comment '0 - 历史版本，1 - 当前有效版本',
    generatedAt      datetime not null comment '生成时间',
    createTime       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete         tinyint default 0 not null comment '是否删除',
    unique key uk_ai_profile_embedding_user_version (userId, profileVersion),
    key idx_ai_profile_embedding_user_status (userId, status)
) comment 'AI 用户画像版本化向量';

-- AI 队伍检索向量表
create table ai_team_embedding
(
    id               bigint auto_increment comment 'id' primary key,
    teamId           bigint not null comment '队伍 id',
    contentVersion   int not null comment '检索文本版本号',
    contentHash      char(64) not null comment '检索文本 SHA-256',
    embeddingModel   varchar(128) not null comment 'Embedding 模型',
    dimensions       int not null comment '向量维度',
    vectorJson       mediumtext not null comment '归一化后的 float 向量 JSON',
    status           tinyint default 1 not null comment '0 - 历史版本，1 - 当前有效版本',
    generatedAt      datetime not null comment '生成时间',
    createTime       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete         tinyint default 0 not null comment '是否删除',
    unique key uk_ai_team_embedding_team_version (teamId, contentVersion),
    key idx_ai_team_embedding_team_status (teamId, status)
) comment 'AI 队伍版本化检索向量';

-- AI 聊天会话与滚动摘要表
create table ai_chat_session
(
    id                             bigint auto_increment comment 'id' primary key,
    userId                         bigint not null comment '用户 id',
    sessionKey                     varchar(64) not null comment 'API 会话标识',
    summary                        text null comment '滚动会话摘要',
    lastSummaryMessageId           bigint default 0 not null comment '摘要已覆盖的消息 ID',
    summaryVersion                 int default 0 not null comment '摘要 CAS 版本',
    summaryUpdatedAt               datetime null comment '摘要更新时间',
    summaryModel                   varchar(128) null comment '摘要模型',
    summaryPromptVersion           varchar(64) null comment '摘要 Prompt 版本',
    lastClosedMessageId            bigint default 0 not null comment '已完成回复的消息 ID',
    lastEpisodeExtractedMessageId  bigint default 0 not null comment '已提取为 Episode 的消息 ID',
    createTime                     datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime                     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete                       tinyint default 0 not null comment '是否删除',
    unique key uk_ai_chat_session_user_key (userId, sessionKey),
    key idx_ai_chat_session_user_time (userId, updateTime)
) comment 'AI 聊天会话和滚动摘要';

-- AI 原始聊天消息表（唯一的对话事实来源）
create table ai_chat_message
(
    id                  bigint auto_increment comment 'id' primary key,
    userId              bigint not null comment '用户 id',
    chatSessionId       bigint not null comment 'ai_chat_session.id',
    role                varchar(16) not null comment 'user / assistant / event',
    content             varchar(2048) null comment '展示文本或事件文本，最小化脱敏',
    responseJson        mediumtext null comment 'AI 响应或事件载荷 JSON',
    visible             tinyint default 1 not null comment '是否在聊天页展示',
    retentionExpireAt   datetime null comment '长期保留过期时间',
    createTime          datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime          datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete            tinyint default 0 not null comment '是否删除',
    key idx_ai_chat_message_session_id (chatSessionId, id),
    key idx_ai_chat_message_user_time (userId, createTime),
    key idx_ai_chat_message_retention (retentionExpireAt)
) comment 'AI 原始聊天消息和业务事件';

-- Episode 提取可靠任务表
create table ai_episode_extraction_task
(
    id                      bigint auto_increment comment 'id' primary key,
    userId                  bigint not null,
    chatSessionId           bigint null,
    sourceType              varchar(32) not null comment 'CHAT_MESSAGE / SELF_INTRODUCTION',
    sourceText              varchar(2048) null comment '非聊天来源的脱敏文本快照',
    sourceReferenceId       varchar(128) null,
    fromMessageIdExclusive  bigint null,
    toMessageIdInclusive    bigint null,
    status                  varchar(16) not null comment 'PENDING / PROCESSING / SUCCESS / FAILED / SUPERSEDED',
    retryCount              int default 0 not null,
    nextRetryAt             datetime null,
    lastError               varchar(1024) null,
    model                   varchar(128) null,
    promptVersion           varchar(64) null,
    createTime              datetime default CURRENT_TIMESTAMP null,
    updateTime              datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete                tinyint default 0 not null,
    unique key uk_ai_episode_extract_range (chatSessionId, fromMessageIdExclusive, toMessageIdInclusive),
    key idx_ai_episode_extract_status_retry (status, nextRetryAt),
    key idx_ai_episode_extract_user_source (userId, sourceType, createTime)
) comment 'Episode 提取可靠任务';

create table ai_user_episode
(
    id                          bigint auto_increment comment 'id' primary key,
    userId                      bigint not null,
    profileType                 varchar(64) not null,
    content                     varchar(1024) not null,
    sourceType                  varchar(32) not null,
    sourceSessionId             bigint null,
    sourceMessageIds            json null,
    sourceReferenceId           varchar(128) null,
    signalType                  varchar(16) not null comment 'INFERRED / EXPLICIT / CORRECTION',
    priority                    varchar(16) not null comment 'NORMAL / IMMEDIATE',
    evidenceGroupKey            varchar(128) not null,
    dedupeHash                  char(64) not null,
    extractionTaskId            bigint null,
    supersededEpisodeIds        json null comment '当前纠正证据明确替代的 Episode ID',
    status                      varchar(16) not null comment 'PENDING / CONSOLIDATED / INVALID',
    consolidatedProfileVersion  int null,
    observedAt                  datetime not null,
    createTime                  datetime default CURRENT_TIMESTAMP null,
    updateTime                  datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete                    tinyint default 0 not null,
    unique key uk_ai_episode_task_dedupe (extractionTaskId, dedupeHash),
    key idx_ai_episode_user_type_status_time (userId, profileType, status, observedAt),
    key idx_ai_episode_source_session (sourceSessionId)
) comment '用户画像证据 Episode';

create table ai_profile_update_task
(
    id                      bigint auto_increment comment 'id' primary key,
    userId                  bigint not null,
    profileType             varchar(64) not null,
    triggerType             varchar(32) not null,
    targetEvidenceDigest    char(64) not null,
    expectedProfileVersion  int null,
    status                  varchar(16) not null comment 'PENDING / PROCESSING / SUCCESS / FAILED / SUPERSEDED',
    retryCount              int default 0 not null,
    nextRetryAt             datetime null,
    lastError               varchar(1024) null,
    model                   varchar(128) null,
    promptVersion           varchar(64) null,
    createTime              datetime default CURRENT_TIMESTAMP null,
    updateTime              datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete                tinyint default 0 not null,
    unique key uk_ai_profile_update_digest (userId, profileType, targetEvidenceDigest),
    key idx_ai_profile_update_status_retry (status, nextRetryAt)
) comment '统一画像更新任务';

create table ai_user_profile_revision
(
    id                  bigint auto_increment comment 'id' primary key,
    userId              bigint not null,
    profileType         varchar(64) not null,
    fromProfileVersion  int null,
    toProfileVersion    int not null,
    triggerType         varchar(32) not null,
    oldContent          text null,
    newContent          text not null,
    evidenceEpisodeIds  json not null,
    model               varchar(128) not null,
    promptVersion       varchar(64) not null,
    createTime          datetime default CURRENT_TIMESTAMP null,
    updateTime          datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete            tinyint default 0 not null,
    key idx_ai_profile_revision_user_type_version (userId, profileType, toProfileVersion)
) comment '画像版本修订记录';

-- 受控活动标签分类表
create table tag_category
(
    id          bigint auto_increment comment 'id' primary key,
    code        varchar(64) not null comment '稳定分类代码',
    name        varchar(64) not null comment '分类名称',
    description varchar(512) null comment '分类说明',
    status      tinyint default 1 not null comment '0-禁用，1-启用',
    sortOrder   int default 0 not null comment '排序值',
    createTime  datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete    tinyint default 0 not null comment '是否删除',
    unique key uk_tag_category_code (code),
    unique key uk_tag_category_name (name),
    key idx_tag_category_status_sort (status, sortOrder)
) comment '活动标签分类';

-- 受控活动标签。向量直接保存在标签表，当前规模无需独立向量表。
create table tag
(
    id                  bigint auto_increment comment 'id' primary key,
    categoryId          bigint not null comment '标签分类 id',
    code                varchar(64) not null comment '稳定标签代码',
    name                varchar(64) not null comment '标准标签名称',
    description         varchar(512) not null comment '标签语义说明',
    status              tinyint default 1 not null comment '0-禁用，1-启用',
    sortOrder           int default 0 not null comment '排序值',
    embeddingTextHash   char(64) null comment '标签向量原文 SHA-256',
    embeddingModel      varchar(128) null comment 'Embedding 模型',
    embeddingDimensions int null comment '向量维度',
    vectorJson          mediumtext null comment '归一化后的 float 向量 JSON',
    embeddingUpdatedAt  datetime null comment '向量更新时间',
    createTime          datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime          datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    isDelete            tinyint default 0 not null comment '是否删除',
    unique key uk_tag_code (code),
    unique key uk_tag_category_name (categoryId, name),
    key idx_tag_category_status_sort (categoryId, status, sortOrder)
) comment '受控活动标签';
