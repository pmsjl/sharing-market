create table ai_agent_trace
(
    id                bigint                             not null comment '应用侧雪花 ID'
        primary key,
    requestId         varchar(64)                        not null comment '全链路请求 ID',
    conversationId    bigint                             not null comment '所属 AI 会话 ID',
    messageId         bigint                             null comment '对应的 ASSISTANT 消息 ID',
    toolName          varchar(128)                       not null comment 'Agent 工具名称',
    toolArguments     json                               null comment '工具参数，敏感字段必须脱敏',
    toolResultSummary json                               null comment '工具结果摘要，不保存完整敏感数据',
    status            varchar(16)                        not null comment 'PENDING / SUCCESS / FAILED',
    latencyMs         int unsigned                       null comment '工具调用耗时',
    errorMessage      text                               null comment '脱敏后的内部错误摘要',
    createTime        datetime default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment 'AI Agent 工具调用审计轨迹' collate = utf8mb4_unicode_ci;

create index idx_conversation_create_time
    on ai_agent_trace (conversationId, createTime);

create index idx_message_id
    on ai_agent_trace (messageId);

create index idx_request_id
    on ai_agent_trace (requestId);

create index idx_tool_name_status
    on ai_agent_trace (toolName, status);

create table ai_conversation
(
    id                 bigint                                 not null comment '应用侧雪花 ID'
        primary key,
    userId             bigint                                 not null comment '会话所属用户 ID',
    title              varchar(120) default '新咨询'          not null comment '会话标题',
    scene              varchar(32)  default 'SHOPPING_GUIDE'  not null comment '会话场景',
    shoppingContext    json                                   null comment '预算、场景、偏好和避雷项',
    memorySummary      text                                   null comment '供 Agent 使用的压缩会话记忆',
    status             varchar(16)  default 'ACTIVE'          not null comment 'ACTIVE / ARCHIVED',
    lastMessagePreview varchar(255)                           null comment '会话列表中的最后消息摘要',
    lastMessageTime    datetime     default CURRENT_TIMESTAMP not null comment '最后消息时间',
    createTime         datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime         datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete           tinyint      default 0                 not null comment '逻辑删除'
)
    comment 'AI 会话' collate = utf8mb4_unicode_ci;

create index idx_user_deleted_last_message
    on ai_conversation (userId, isDelete, lastMessageTime);

create index idx_user_scene_status
    on ai_conversation (userId, scene, status);

create table ai_message
(
    id                bigint                                not null comment '应用侧雪花 ID'
        primary key,
    conversationId    bigint                                not null comment '所属 AI 会话 ID',
    userId            bigint                                not null comment '会话所属用户 ID',
    sequenceNo        int unsigned                          not null comment '会话内递增消息序号',
    role              varchar(16)                           not null comment 'USER / ASSISTANT',
    content           longtext                              not null comment '用户输入或面向用户的 AI 回答',
    structuredContent json                                  null comment '可选购买条件或 Agent 结构化输出',
    status            varchar(16) default 'SUCCESS'         not null comment 'PENDING / SUCCESS / FAILED',
    requestId         varchar(64)                           not null comment '一次前端请求的全链路 ID',
    modelName         varchar(128)                          null comment '生成回答的模型名称',
    inputTokens       int unsigned                          null comment '模型输入 token 数',
    outputTokens      int unsigned                          null comment '模型输出 token 数',
    latencyMs         int unsigned                          null comment '本次回答总耗时',
    agentErrorKey     varchar(64)                           null comment '稳定的失败错误码',
    retryable         tinyint                               null comment '失败是否允许用户手动重试',
    createTime        datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime        datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete          tinyint     default 0                 not null comment '逻辑删除',
    constraint uk_conversation_sequence
        unique (conversationId, sequenceNo),
    constraint uk_request_role
        unique (requestId, role)
)
    comment 'AI 会话消息' collate = utf8mb4_unicode_ci;

create index idx_conversation_deleted_sequence
    on ai_message (conversationId, isDelete, sequenceNo);

create index idx_pending_expire_scan
    on ai_message (status, role, isDelete, createTime);

create index idx_user_create_time
    on ai_message (userId, createTime);

create index idx_user_pending_message
    on ai_message (userId, status, role, isDelete, createTime);

create table ai_usage_daily
(
    id              bigint                                    not null comment '应用侧雪花 ID'
        primary key,
    userId          bigint                                    not null comment '用户 ID',
    usageDate       date                                      not null comment 'Asia/Shanghai 自然日',
    requestCount    int unsigned    default '0'               not null comment '已派发 Agent 请求数',
    successCount    int unsigned    default '0'               not null comment '成功请求数',
    failedCount     int unsigned    default '0'               not null comment '失败请求数',
    inputTokens     bigint unsigned default '0'               not null comment '成功请求输入 token 合计',
    outputTokens    bigint unsigned default '0'               not null comment '成功请求输出 token 合计',
    lastRequestTime datetime                                  null comment '最近一次预占时间',
    createTime      datetime        default CURRENT_TIMESTAMP not null,
    updateTime      datetime        default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint uk_user_usage_date
        unique (userId, usageDate)
)
    comment '用户 AI 每日用量' collate = utf8mb4_unicode_ci;

create table ai_usage_global_daily
(
    usageDate       date                                      not null comment 'Asia/Shanghai 自然日'
        primary key,
    requestCount    int unsigned    default '0'               not null comment '全平台已派发 Agent 请求数',
    successCount    int unsigned    default '0'               not null comment '全平台成功请求数',
    failedCount     int unsigned    default '0'               not null comment '全平台失败请求数',
    inputTokens     bigint unsigned default '0'               not null comment '成功请求输入 token 合计',
    outputTokens    bigint unsigned default '0'               not null comment '成功请求输出 token 合计',
    lastRequestTime datetime                                  null comment '最近一次预占时间',
    createTime      datetime        default CURRENT_TIMESTAMP not null,
    updateTime      datetime        default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
)
    comment '全平台 AI 每日用量' collate = utf8mb4_unicode_ci;

create table barrage
(
    id         bigint auto_increment comment 'id'
        primary key,
    message    varchar(255)                       not null comment '弹幕文本',
    userAvatar varchar(1024)                      not null comment '用户头像',
    userId     bigint                             not null comment '用户id',
    isSelected tinyint  default 0                 not null comment '是否精选（默认0，精选为1）',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除'
)
    charset = utf8mb3
    row_format = DYNAMIC;

create table campus_coin_transaction
(
    id              bigint                             not null comment '应用侧雪花 ID'
        primary key,
    userId          bigint                             not null comment '校园币所属用户 ID',
    amount          decimal(12, 2)                     not null comment '本次变动金额，收入为正、支出为负',
    balanceBefore   decimal(12, 2)                     not null comment '变动前余额',
    balanceAfter    decimal(12, 2)                     not null comment '变动后余额',
    transactionType varchar(32)                        not null comment 'OPENING_BALANCE / REGISTER_GRANT / ADMIN_GRANT / PURCHASE',
    businessId      varchar(64)                        not null comment '幂等业务标识',
    operatorId      bigint                             null comment '管理员发放时的操作人 ID',
    remark          varchar(255)                       null comment '流水说明',
    createTime      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint uk_type_business
        unique (transactionType, businessId)
)
    comment '校园币不可变流水' collate = utf8mb4_unicode_ci;

create index idx_user_create_time
    on campus_coin_transaction (userId, createTime);

create table comment
(
    id         bigint auto_increment comment '评论 ID'
        primary key,
    postId     bigint                             not null comment '面经帖子 ID',
    userId     bigint                             not null comment '用户 ID',
    content    text                               not null comment '评论内容',
    parentId   bigint                             null comment '父评论 ID，支持多级嵌套回复',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    ancestorId bigint                             null
)
    charset = utf8mb3
    row_format = DYNAMIC;

create index comment_questionId
    on comment (postId);

create table commodity
(
    id                   bigint auto_increment comment '商品 ID'
        primary key,
    commodityName        varchar(255)                       not null comment '商品名称',
    commodityDescription varchar(2048)                      null comment '商品简介',
    commodityAvatar      varchar(1024)                      null comment '商品封面图',
    degree               varchar(255)                       null comment '商品新旧程度（例如 9成新）',
    commodityTypeId      bigint                             null comment '商品分类 ID',
    adminId              bigint                             not null comment '管理员 ID （某人创建该商品）',
    isListed             tinyint  default 0                 null comment '是否上架（默认0未上架，1已上架）',
    commodityInventory   int      default 0                 null comment '商品数量（默认0）',
    price                decimal(10, 2)                     not null comment '商品价格',
    viewNum              int      default 0                 null comment '商品浏览量',
    favourNum            int      default 0                 null comment '商品收藏量',
    createTime           datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime           datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete             tinyint  default 0                 null comment '是否删除'
)
    charset = utf8mb3
    row_format = DYNAMIC;

create index name_index
    on commodity (commodityName);

create index type_index
    on commodity (commodityTypeId);

create table commodity_order
(
    id            bigint auto_increment comment '订单 ID'
        primary key,
    userId        bigint                             not null comment '用户 ID',
    commodityId   bigint                             not null comment '商品 ID',
    remark        varchar(1024)                      null comment '订单备注',
    buyNumber     int                                null comment '购买数量',
    paymentAmount decimal(10, 2)                     null comment '订单总支付金额',
    payStatus     tinyint  default 0                 null comment '0-未支付 1-已支付',
    createTime    datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint  default 0                 null comment '是否删除'
)
    charset = utf8mb3
    row_format = DYNAMIC;

create index idx_pay_status_delete_create_time
    on commodity_order (payStatus, isDelete, createTime);

create table commodity_score
(
    id          bigint auto_increment comment '商品评分 ID'
        primary key,
    commodityId bigint                             not null comment '商品 ID',
    userId      bigint                             not null comment '用户 ID',
    score       int                                not null comment '评分（0-5，星级评分）',
    createTime  datetime default CURRENT_TIMESTAMP null comment '创建时间
',
    updateTime  datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 null comment '是否删除',
    constraint scoreId
        unique (commodityId, userId)
)
    charset = utf8mb3
    row_format = DYNAMIC;

create table commodity_type
(
    id         bigint auto_increment comment '商品分类 ID'
        primary key,
    typeName   varchar(255)                       not null comment '商品类别名称',
    createTime datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 null comment '是否删除'
)
    charset = utf8mb3
    row_format = DYNAMIC;

create table notice
(
    id            bigint auto_increment comment 'id'
        primary key,
    noticeTitle   varchar(255)                       not null comment '公告标题',
    noticeContent varchar(255)                       not null comment '公告内容',
    noticeAdminId bigint                             not null comment '创建人id（管理员）',
    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint  default 0                 not null comment '是否删除'
)
    charset = utf8mb3
    row_format = DYNAMIC;

create table post
(
    id         bigint auto_increment comment 'id'
        primary key,
    title      varchar(512)                       null comment '标题',
    content    text                               null comment '内容',
    tags       varchar(1024)                      null comment '标签列表（json 数组）',
    thumbNum   int      default 0                 not null comment '点赞数',
    favourNum  int      default 0                 not null comment '收藏数',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除'
)
    comment '帖子' collate = utf8mb4_unicode_ci
                   row_format = DYNAMIC;

create index idx_userId
    on post (userId);

create table post_favour
(
    id         bigint auto_increment comment 'id'
        primary key,
    postId     bigint                             not null comment '帖子 id',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_user_post
        unique (userId, postId)
)
    comment '帖子收藏' charset = utf8mb3
                       row_format = DYNAMIC;

create index idx_postId
    on post_favour (postId);

create index idx_userId
    on post_favour (userId);

create table post_thumb
(
    id         bigint auto_increment comment 'id'
        primary key,
    postId     bigint                             not null comment '帖子 id',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '帖子点赞' charset = utf8mb3
                       row_format = DYNAMIC;

create index idx_postId
    on post_thumb (postId);

create index idx_userId
    on post_thumb (userId);

create table private_message
(
    id          bigint auto_increment comment '消息 ID'
        primary key,
    senderId    bigint                                   not null comment '发送者 ID',
    recipientId bigint                                   not null comment '接收者 ID',
    content     varchar(4096) collate utf8mb4_unicode_ci null comment '消息内容(UTF8MB4 支持Emoji表情)',
    alreadyRead tinyint  default 0                       null comment '0-未阅读 1-已阅读',
    type        varchar(255)                             not null comment '消息发送类型（用户发送还是管理员发送,user Or admin)枚举',
    isRecalled  tinyint  default 0                       null comment '是否撤回  0-未撤回 1-已撤回',
    createTime  datetime default CURRENT_TIMESTAMP       null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP       null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                       null comment '是否删除'
)
    charset = utf8mb3
    row_format = DYNAMIC;

create table user
(
    id           bigint auto_increment comment 'id'
        primary key,
    userAccount  varchar(256)                             not null comment '账号',
    userPassword varchar(512)                             not null comment '密码',
    unionId      varchar(256)                             null comment '微信开放平台id',
    mpOpenId     varchar(256)                             null comment '公众号openId',
    userName     varchar(256)                             null comment '用户昵称',
    userAvatar   varchar(1024)                            null comment '用户头像',
    userProfile  varchar(512)                             null comment '用户简介',
    userRole     varchar(256)   default 'user'            not null comment '用户角色：user/admin/ban',
    userPhone    varchar(255)                             null comment '联系电话',
    balance      decimal(12, 2) default 0.00              not null comment '不可充值、不可提现的校园币余额',
    editTime     datetime       default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime       default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime       default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint        default 0                 not null comment '是否删除',
    constraint uk_userAccount
        unique (userAccount)
)
    comment '用户' collate = utf8mb4_unicode_ci
                   row_format = DYNAMIC;

create index idx_unionId
    on user (unionId);

create table user_commodity_favorites
(
    id          bigint auto_increment
        primary key,
    userId      bigint                             not null comment '用户 ID',
    commodityId bigint                             not null comment '商品 ID',
    status      tinyint  default 1                 null comment '1-正常收藏 0-取消收藏',
    remark      varchar(255)                       null comment '用户备注',
    createTime  datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 null comment '是否删除',
    constraint unique_favorite
        unique (userId, commodityId)
)
    charset = utf8mb3
    row_format = DYNAMIC;


