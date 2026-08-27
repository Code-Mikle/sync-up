create database if not exists sync_up_db;

use sync_up_db;

TRUNCATE TABLE user;
# TRUNCATE TABLE ai_tool_call_log;
# TRUNCATE TABLE user_team;
# TRUNCATE TABLE ai_team_embedding;
TRUNCATE TABLE ai_team_draft;

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
    profile      varchar(1024) null comment '个人简介 / 自我介绍'
) comment '用户';

create unique index uk_user_userAccount on user (userAccount);

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
    isDelete    tinyint  default 0 not null comment '是否删除'
) comment '队伍';

create index idx_team_userId on team (userId);
create index idx_team_search on team (status, city, activityCategory, startTime);

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
    isDelete                    tinyint default 0 not null comment '是否删除'
) comment 'AI 内部用户文本画像';

create unique index uk_ai_user_profile_userId on ai_user_profile (userId);
create index idx_ai_user_profile_status_updateTime on ai_user_profile (status, updateTime);

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
    isDelete         tinyint default 0 not null comment '是否删除'
) comment 'AI 用户画像版本化向量';

create unique index uk_ai_profile_embedding_user_version on ai_user_profile_embedding (userId, profileVersion);
create index idx_ai_profile_embedding_user_status on ai_user_profile_embedding (userId, status);

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
    isDelete         tinyint default 0 not null comment '是否删除'
) comment 'AI 队伍版本化检索向量';

create unique index uk_ai_team_embedding_team_version on ai_team_embedding (teamId, contentVersion);
create index idx_ai_team_embedding_team_status on ai_team_embedding (teamId, status);

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
    isDelete                       tinyint default 0 not null comment '是否删除'
) comment 'AI 聊天会话和滚动摘要';

create unique index uk_ai_chat_session_user_key on ai_chat_session (userId, sessionKey);
create index idx_ai_chat_session_user_time on ai_chat_session (userId, updateTime);

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
    isDelete            tinyint default 0 not null comment '是否删除'
) comment 'AI 原始聊天消息和业务事件';

create index idx_ai_chat_message_session_id on ai_chat_message (chatSessionId, id);
create index idx_ai_chat_message_user_time on ai_chat_message (userId, createTime);
create index idx_ai_chat_message_retention on ai_chat_message (retentionExpireAt);

-- Episode 提取可靠任务表
create table ai_episode_extraction_task
(
    id                      bigint auto_increment comment 'id' primary key,
    userId                  bigint not null,
    chatSessionId           bigint null,
    sourceType              varchar(32) not null comment 'CHAT_MESSAGE / SELF_INTRODUCTION',
    sourceText              text null comment '非聊天来源快照',
    sourceReferenceId       varchar(128) null,
    fromMessageIdExclusive  bigint null,
    toMessageIdInclusive    bigint null,
    status                  varchar(16) not null comment 'PENDING / PROCESSING / SUCCESS / FAILED',
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
    signalType                  varchar(16) not null,
    priority                    varchar(16) not null,
    evidenceGroupKey            varchar(128) not null,
    dedupeHash                  char(64) not null,
    extractionTaskId            bigint null,
    supersededEpisodeIds        json null comment '当前纠正证据明确替代的 Episode ID',
    status                      varchar(16) not null,
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
    status                  varchar(16) not null,
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
    isDelete    tinyint default 0 not null comment '是否删除'
) comment '活动标签分类';

create unique index uk_tag_category_code on tag_category (code);
create unique index uk_tag_category_name on tag_category (name);
create index idx_tag_category_status_sort on tag_category (status, sortOrder);

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
    isDelete            tinyint default 0 not null comment '是否删除'
) comment '受控活动标签';

create unique index uk_tag_code on tag (code);
create unique index uk_tag_category_name on tag (categoryId, name);
create index idx_tag_category_status_sort on tag (categoryId, status, sortOrder);
