declare namespace API {
  type BaseResponse = {
    code?: number;
    data?: Record<string, any>;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseBoolean_ = {
    code?: number;
    data?: boolean;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseCommentVO_ = {
    code?: number;
    data?: CommentVO;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseCommodityOrderVO_ = {
    code?: number;
    data?: CommodityOrderVO;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseCommodityScoreVO_ = {
    code?: number;
    data?: CommodityScoreVO;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseCommodityTypeVO_ = {
    code?: number;
    data?: CommodityTypeVO;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseCommodityVO_ = {
    code?: number;
    data?: CommodityVO;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseInt_ = {
    code?: number;
    data?: number;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseListCommentVO_ = {
    code?: number;
    data?: CommentVO[];
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseListCommodity_ = {
    code?: number;
    data?: Commodity[];
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseListMapStringObject_ = {
    code?: number;
    data?: MapStringObject_[];
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseListMyCommentVO_ = {
    code?: number;
    data?: MyCommentVO[];
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseLoginUserVO_ = {
    code?: number;
    data?: LoginUserVO;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseLong_ = {
    code?: number;
    data?: number;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseMapStringObject_ = {
    code?: number;
    data?: Record<string, any>;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseNoticeVO_ = {
    code?: number;
    data?: NoticeVO;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageComment_ = {
    code?: number;
    data?: PageComment_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageCommentVO_ = {
    code?: number;
    data?: PageCommentVO_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageCommodity_ = {
    code?: number;
    data?: PageCommodity_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageCommodityOrder_ = {
    code?: number;
    data?: PageCommodityOrder_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageCommodityOrderVO_ = {
    code?: number;
    data?: PageCommodityOrderVO_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageCommodityScore_ = {
    code?: number;
    data?: PageCommodityScore_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageCommodityScoreVO_ = {
    code?: number;
    data?: PageCommodityScoreVO_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageCommodityType_ = {
    code?: number;
    data?: PageCommodityType_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageCommodityTypeVO_ = {
    code?: number;
    data?: PageCommodityTypeVO_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageCommodityVO_ = {
    code?: number;
    data?: PageCommodityVO_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageNotice_ = {
    code?: number;
    data?: PageNotice_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageNoticeVO_ = {
    code?: number;
    data?: PageNoticeVO_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePagePost_ = {
    code?: number;
    data?: PagePost_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePagePostVO_ = {
    code?: number;
    data?: PagePostVO_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePagePrivateMessage_ = {
    code?: number;
    data?: PagePrivateMessage_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePagePrivateMessageVO_ = {
    code?: number;
    data?: PagePrivateMessageVO_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageUser_ = {
    code?: number;
    data?: PageUser_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageUserCommodityFavoritesVO_ = {
    code?: number;
    data?: PageUserCommodityFavoritesVO_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePageUserVO_ = {
    code?: number;
    data?: PageUserVO_;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePostVO_ = {
    code?: number;
    data?: PostVO;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponsePrivateMessageVO_ = {
    code?: number;
    data?: PrivateMessageVO;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseString_ = {
    code?: number;
    data?: string;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseUser_ = {
    code?: number;
    data?: User;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BaseResponseUserVO_ = {
    code?: number;
    data?: UserVO;
    hashMap?: Record<string, any>;
    message?: string;
  };

  type BuyCommodityRequest = {
    buyNumber?: number;
    commodityId?: string;
    payStatus?: number;
    paymentAmount?: number;
    remark?: string;
  };

  type checkUsingGETParams = {
    /** echostr */
    echostr?: string;
    /** nonce */
    nonce?: string;
    /** signature */
    signature?: string;
    /** timestamp */
    timestamp?: string;
  };

  type Comment = {
    ancestorId?: string;
    content?: string;
    createTime?: string;
    id?: string;
    isDelete?: number;
    parentId?: string;
    postId?: string;
    updateTime?: string;
    userId?: string;
  };

  type CommentAddRequest = {
    content?: string;
    parentId?: string;
    postId?: string;
  };

  type CommentEditRequest = {
    content?: string;
    id?: string;
  };

  type CommentQueryRequest = {
    ancestorId?: string;
    content?: string;
    current?: number;
    id?: string;
    pageSize?: number;
    parentId?: string;
    postId?: string;
    sortField?: string;
    sortOrder?: string;
    userId?: string;
  };

  type CommentUpdateRequest = {
    content?: string;
    id?: string;
    parentId?: string;
    postId?: string;
    userId?: string;
  };

  type CommentVO = {
    ancestorId?: string;
    content?: string;
    createTime?: string;
    id?: string;
    parentId?: string;
    postId?: string;
    repliedUser?: UserVO;
    replies?: CommentVO[];
    updateTime?: string;
    user?: UserVO;
    userId?: string;
  };

  type Commodity = {
    adminId?: string;
    commodityAvatar?: string;
    commodityDescription?: string;
    commodityInventory?: number;
    commodityName?: string;
    commodityTypeId?: string;
    createTime?: string;
    degree?: string;
    favourNum?: number;
    id?: string;
    isDelete?: number;
    isListed?: number;
    price?: number;
    updateTime?: string;
    viewNum?: number;
  };

  type CommodityAddRequest = {
    adminId?: string;
    commodityAvatar?: string;
    commodityDescription?: string;
    commodityInventory?: number;
    commodityName?: string;
    commodityTypeId?: string;
    degree?: string;
    favourNum?: number;
    isListed?: number;
    price?: number;
    viewNum?: number;
  };

  type CommodityEditRequest = {
    adminId?: string;
    commodityAvatar?: string;
    commodityDescription?: string;
    commodityInventory?: number;
    commodityName?: string;
    commodityTypeId?: string;
    degree?: string;
    favourNum?: number;
    id?: string;
    isListed?: number;
    price?: number;
    viewNum?: number;
  };

  type CommodityOrder = {
    buyNumber?: number;
    commodityId?: string;
    createTime?: string;
    id?: string;
    isDelete?: number;
    payStatus?: number;
    paymentAmount?: number;
    remark?: string;
    updateTime?: string;
    userId?: string;
  };

  type CommodityOrderAddRequest = {
    buyNumber?: number;
    commodityId?: string;
    payStatus?: number;
    paymentAmount?: number;
    remark?: string;
    userId?: string;
  };

  type CommodityOrderEditRequest = {
    id?: string;
    payStatus?: number;
    remark?: string;
  };

  type CommodityOrderQueryRequest = {
    buyNumber?: number;
    commodityId?: string;
    current?: number;
    id?: string;
    pageSize?: number;
    payStatus?: number;
    paymentAmount?: number;
    remark?: string;
    sortField?: string;
    sortOrder?: string;
    userId?: string;
    userName?: string;
    userPhone?: string;
  };

  type CommodityOrderUpdateRequest = {
    buyNumber?: number;
    commodityId?: string;
    id?: string;
    payStatus?: number;
    paymentAmount?: number;
    remark?: string;
    userId?: string;
  };

  type CommodityOrderVO = {
    buyNumber?: number;
    commodityId?: string;
    commodityName?: string;
    createTime?: string;
    id?: string;
    isDelete?: number;
    payStatus?: number;
    paymentAmount?: number;
    remark?: string;
    updateTime?: string;
    userId?: string;
    userName?: string;
    userPhone?: string;
  };

  type CommodityQueryRequest = {
    adminId?: string;
    commodityDescription?: string;
    commodityInventory?: number;
    commodityName?: string;
    commodityTypeId?: string;
    current?: number;
    degree?: string;
    favourNum?: number;
    id?: string;
    isListed?: number;
    pageSize?: number;
    price?: number;
    sortField?: string;
    sortOrder?: string;
    viewNum?: number;
  };

  type CommodityScore = {
    commodityId?: string;
    createTime?: string;
    id?: string;
    isDelete?: number;
    score?: number;
    updateTime?: string;
    userId?: string;
  };

  type CommodityScoreAddRequest = {
    commodityId?: string;
    score?: number;
    userId?: string;
  };

  type CommodityScoreEditRequest = {
    commodityId?: string;
    id?: string;
    score?: number;
    userId?: string;
  };

  type CommodityScoreQueryRequest = {
    commodityId?: string;
    current?: number;
    id?: string;
    pageSize?: number;
    score?: number;
    sortField?: string;
    sortOrder?: string;
    userId?: string;
  };

  type CommodityScoreUpdateRequest = {
    commodityId?: string;
    id?: string;
    score?: number;
    userId?: string;
  };

  type CommodityScoreVO = {
    commodityId?: string;
    createTime?: string;
    id?: string;
    score?: number;
    updateTime?: string;
    userId?: string;
    userVO?: UserVO;
  };

  type CommodityType = {
    createTime?: string;
    id?: string;
    isDelete?: number;
    typeName?: string;
    updateTime?: string;
  };

  type CommodityTypeAddRequest = {
    typeName?: string;
  };

  type CommodityTypeEditRequest = {
    id?: string;
    typeName?: string;
  };

  type CommodityTypeQueryRequest = {
    current?: number;
    id?: string;
    pageSize?: number;
    sortField?: string;
    sortOrder?: string;
    typeName?: string;
  };

  type CommodityTypeUpdateRequest = {
    id?: string;
    typeName?: string;
  };

  type CommodityTypeVO = {
    createTime?: string;
    id?: string;
    typeName?: string;
    updateTime?: string;
  };

  type CommodityUpdateRequest = {
    adminId?: string;
    commodityAvatar?: string;
    commodityDescription?: string;
    commodityInventory?: number;
    commodityName?: string;
    commodityTypeId?: string;
    degree?: string;
    favourNum?: number;
    id?: string;
    isListed?: number;
    price?: number;
    viewNum?: number;
  };

  type CommodityVO = {
    adminId?: string;
    adminName?: string;
    commodityAvatar?: string;
    commodityDescription?: string;
    commodityInventory?: number;
    commodityName?: string;
    commodityTypeId?: string;
    commodityTypeName?: string;
    createTime?: string;
    degree?: string;
    favourNum?: number;
    id?: string;
    isListed?: number;
    price?: number;
    updateTime?: string;
    viewNum?: number;
  };

  type DeleteRequest = {
    id?: string;
  };

  type getAverageScoreUsingGETParams = {
    /** commodityId */
    commodityId: string;
  };

  type getCommentByPostIdUsingGETParams = {
    /** postId */
    postId?: string;
  };

  type getCommentVOByIdUsingGETParams = {
    /** id */
    id?: string;
  };

  type getCommodityOrderHeatmapDataUsingGETParams = {
    /** payStatus */
    payStatus: number;
  };

  type getCommodityOrderVOByIdUsingGETParams = {
    /** id */
    id?: string;
  };

  type getCommodityScoreVOByIdUsingGETParams = {
    /** id */
    id?: string;
  };

  type getCommodityTypeVOByIdUsingGETParams = {
    /** id */
    id?: string;
  };

  type getCommodityVOByIdUsingGETParams = {
    /** id */
    id?: string;
  };

  type getNoticeVOByIdUsingGETParams = {
    /** id */
    id?: string;
  };

  type getPostVOByIdUsingGETParams = {
    /** id */
    id?: string;
  };

  type getPrivateMessageVOByIdUsingGETParams = {
    /** id */
    id?: string;
  };

  type getUserByIdUsingGETParams = {
    /** id */
    id?: string;
  };

  type getUserVOByIdUsingGETParams = {
    /** id */
    id?: string;
  };

  type LoginUserVO = {
    balance?: number;
    createTime?: string;
    id?: string;
    updateTime?: string;
    userAvatar?: string;
    userName?: string;
    userProfile?: string;
    userRole?: string;
  };

  type MapStringObject_ = true;

  type MyCommentVO = {
    content?: string;
    id?: string;
    postId?: string;
    postTitle?: string;
    updateTime?: string;
  };

  type Notice = {
    createTime?: string;
    id?: string;
    isDelete?: number;
    noticeAdminId?: string;
    noticeContent?: string;
    noticeTitle?: string;
    updateTime?: string;
  };

  type NoticeAddRequest = {
    noticeAdminId?: string;
    noticeContent?: string;
    noticeTitle?: string;
  };

  type NoticeQueryRequest = {
    current?: number;
    id?: string;
    noticeAdminId?: string;
    noticeContent?: string;
    noticeTitle?: string;
    pageSize?: number;
    sortField?: string;
    sortOrder?: string;
  };

  type NoticeUpdateRequest = {
    id?: string;
    noticeAdminId?: string;
    noticeContent?: string;
    noticeTitle?: string;
  };

  type NoticeVO = {
    createTime?: string;
    id?: string;
    noticeAdminId?: string;
    noticeContent?: string;
    noticeTitle?: string;
    updateTime?: string;
    user?: UserVO;
  };

  type OrderItem = {
    asc?: boolean;
    column?: string;
  };

  type PageComment_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: Comment[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageCommentVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: CommentVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageCommodity_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: Commodity[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageCommodityOrder_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: CommodityOrder[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageCommodityOrderVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: CommodityOrderVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageCommodityScore_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: CommodityScore[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageCommodityScoreVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: CommodityScoreVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageCommodityType_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: CommodityType[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageCommodityTypeVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: CommodityTypeVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageCommodityVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: CommodityVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageNotice_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: Notice[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageNoticeVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: NoticeVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PagePost_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: Post[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PagePostVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: PostVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PagePrivateMessage_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: PrivateMessage[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PagePrivateMessageVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: PrivateMessageVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageUser_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: User[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageUserCommodityFavoritesVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: UserCommodityFavoritesVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageUserVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: UserVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PayCommodityOrderRequest = {
    commodityOrderId?: string;
  };

  type Post = {
    content?: string;
    createTime?: string;
    favourNum?: number;
    id?: string;
    isDelete?: number;
    tags?: string;
    thumbNum?: number;
    title?: string;
    updateTime?: string;
    userId?: string;
  };

  type PostAddRequest = {
    content?: string;
    tags?: string[];
    title?: string;
  };

  type PostEditRequest = {
    content?: string;
    id?: string;
    tags?: string[];
    title?: string;
  };

  type PostFavourAddRequest = {
    postId?: string;
  };

  type PostFavourQueryRequest = {
    current?: number;
    pageSize?: number;
    postQueryRequest?: PostQueryRequest;
    sortField?: string;
    sortOrder?: string;
    userId?: string;
  };

  type PostQueryRequest = {
    content?: string;
    current?: number;
    favourUserId?: string;
    id?: string;
    notId?: string;
    orTags?: string[];
    pageSize?: number;
    searchText?: string;
    sortField?: string;
    sortOrder?: string;
    tags?: string[];
    title?: string;
    userId?: string;
  };

  type PostThumbAddRequest = {
    postId?: string;
  };

  type PostUpdateRequest = {
    content?: string;
    id?: string;
    tags?: string[];
    title?: string;
  };

  type PostVO = {
    content?: string;
    createTime?: string;
    favourNum?: number;
    hasFavour?: boolean;
    hasThumb?: boolean;
    id?: string;
    tagList?: string[];
    thumbNum?: number;
    title?: string;
    updateTime?: string;
    user?: UserVO;
    userId?: string;
  };

  type PrivateMessage = {
    alreadyRead?: number;
    content?: string;
    createTime?: string;
    id?: string;
    isDelete?: number;
    isRecalled?: number;
    recipientId?: string;
    senderId?: string;
    type?: string;
    updateTime?: string;
  };

  type PrivateMessageAddRequest = {
    alreadyRead?: number;
    content?: string;
    isRecalled?: number;
    recipientId?: string;
    senderId?: string;
    type?: string;
  };

  type PrivateMessageEditRequest = {
    alreadyRead?: number;
    content?: string;
    id?: string;
    isRecalled?: number;
    recipientId?: string;
    senderId?: string;
    type?: string;
  };

  type PrivateMessageQueryRequest = {
    contactUserId?: string;
    current?: number;
    pageSize?: number;
    sortField?: string;
    sortOrder?: string;
  };

  type PrivateMessageUpdateRequest = {
    alreadyRead?: number;
    content?: string;
    id?: string;
    isRecalled?: number;
    recipientId?: string;
    senderId?: string;
    type?: string;
  };

  type PrivateMessageVO = {
    alreadyRead?: number;
    content?: string;
    createTime?: string;
    id?: string;
    isRecalled?: number;
    recipientId?: string;
    senderId?: string;
    type?: string;
    updateTime?: string;
  };

  type uploadFileUsingPOSTParams = {
    biz?: string;
  };

  type User = {
    balance?: number;
    createTime?: string;
    editTime?: string;
    id?: string;
    isDelete?: number;
    mpOpenId?: string;
    unionId?: string;
    updateTime?: string;
    userAccount?: string;
    userAvatar?: string;
    userName?: string;
    userPassword?: string;
    userPhone?: string;
    userProfile?: string;
    userRole?: string;
  };

  type UserAddRequest = {
    userAccount?: string;
    userAvatar?: string;
    userName?: string;
    userPassword?: string;
    userRole?: string;
  };

  type UserCommodityFavoritesAddRequest = {
    commodityId?: string;
  };

  type UserCommodityFavoritesEditRequest = {
    id?: string;
    status?: number;
  };

  type UserCommodityFavoritesQueryRequest = {
    commodityId?: string;
    current?: number;
    id?: string;
    pageSize?: number;
    remark?: string;
    sortField?: string;
    sortOrder?: string;
    status?: number;
    userId?: string;
  };

  type UserCommodityFavoritesVO = {
    adminId?: string;
    commodityAvatar?: string;
    commodityDescription?: string;
    commodityId?: string;
    commodityInventory?: number;
    commodityName?: string;
    commodityTypeId?: string;
    createTime?: string;
    degree?: string;
    favourNum?: number;
    id?: string;
    isListed?: number;
    price?: number;
    remark?: string;
    status?: number;
    updateTime?: string;
    userId?: string;
    viewNum?: number;
  };

  type userLoginByWxOpenUsingGETParams = {
    /** code */
    code: string;
  };

  type UserLoginRequest = {
    userAccount?: string;
    userPassword?: string;
  };

  type UserQueryRequest = {
    balance?: number;
    current?: number;
    id?: string;
    mpOpenId?: string;
    pageSize?: number;
    sortField?: string;
    sortOrder?: string;
    unionId?: string;
    userName?: string;
    userProfile?: string;
    userRole?: string;
  };

  type UserRegisterRequest = {
    checkPassword?: string;
    userAccount?: string;
    userPassword?: string;
  };

  type UserUpdateMyRequest = {
    userAvatar?: string;
    userName?: string;
    userProfile?: string;
  };

  type UserUpdateRequest = {
    balance?: number;
    id?: string;
    userAvatar?: string;
    userName?: string;
    userProfile?: string;
    userRole?: string;
  };

  type UserVO = {
    balance?: number;
    createTime?: string;
    id?: string;
    userAvatar?: string;
    userName?: string;
    userPhone?: string;
    userProfile?: string;
    userRole?: string;
  };
}
