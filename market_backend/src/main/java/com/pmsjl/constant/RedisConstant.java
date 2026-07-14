package com.pmsjl.constant;

public interface RedisConstant {
    String LOGIN_USER_KEY ="login:token:";
    Long LOGIN_USER_TTL=36000L;
    String COMMODITY_TYPE_KEY = "commodity:type:";
    String CACHE_COMMODITY_KEY ="cache:commodity:";
    String COMMODITY_VIEW_NUM_KEY ="commodity:view:num:";
    String CACHE_NOTICE_VO_KEY="cache:notice:vo:";
    String CACHE_NOTICE_LIST_VO_PAGE_KEY ="cache:notice:list:vo:current:1:pageSize:15";
    String POST_FAVOUR_KEY="lock:post:favour:";
    String POST_THUMB_KEY="lock:post:thumb:";

}
