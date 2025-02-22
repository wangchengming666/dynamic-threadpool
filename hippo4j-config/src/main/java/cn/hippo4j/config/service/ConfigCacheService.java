package cn.hippo4j.config.service;

import cn.hippo4j.config.notify.NotifyCenter;
import cn.hutool.core.collection.CollUtil;
import cn.hippo4j.config.service.biz.ConfigService;
import cn.hippo4j.common.config.ApplicationContextHolder;
import cn.hippo4j.common.constant.Constants;
import cn.hippo4j.common.toolkit.Md5Util;
import cn.hippo4j.config.event.LocalDataChangeEvent;
import cn.hippo4j.config.model.CacheItem;
import cn.hippo4j.config.model.ConfigAllInfo;
import cn.hippo4j.config.toolkit.MapUtil;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Config cache service.
 *
 * @author chen.ma
 * @date 2021/6/24 21:19
 */
@Slf4j
public class ConfigCacheService {

    private static ConfigService configService = null;

    /**
     * TODO: 数据结构、客户端停机时 remove 操作待重构
     * <p>
     * key: message-produce+dynamic-threadpool-example+prescription+192.168.20.227:8088
     * val:
     * key: 192.168.20.227:8088
     * val:  {@link CacheItem}
     */
    private static final ConcurrentHashMap<String, Map<String, CacheItem>> CACHE = new ConcurrentHashMap();

    public static boolean isUpdateData(String groupKey, String md5, String ip) {
        String contentMd5 = ConfigCacheService.getContentMd5IsNullPut(groupKey, ip);
        return Objects.equals(contentMd5, md5);
    }

    /**
     * Get Md5.
     *
     * @param groupKey
     * @param ip
     * @return
     */
    private synchronized static String getContentMd5IsNullPut(String groupKey, String ip) {
        Map<String, CacheItem> cacheItemMap = Optional.ofNullable(CACHE.get(groupKey)).orElse(Maps.newHashMap());

        CacheItem cacheItem = null;
        if (CollUtil.isNotEmpty(cacheItemMap) && (cacheItem = cacheItemMap.get(ip)) != null) {
            return cacheItem.md5;
        }

        if (configService == null) {
            configService = ApplicationContextHolder.getBean(ConfigService.class);
        }
        String[] split = groupKey.split("\\+");

        ConfigAllInfo config = configService.findConfigAllInfo(split[0], split[1], split[2]);
        if (config != null && !StringUtils.isEmpty(config.getTpId())) {
            cacheItem = new CacheItem(groupKey, config);
            cacheItemMap.put(ip, cacheItem);
            CACHE.put(groupKey, cacheItemMap);
        }
        return (cacheItem != null) ? cacheItem.md5 : Constants.NULL;
    }

    public static String getContentMd5(String groupKey) {
        if (configService == null) {
            configService = ApplicationContextHolder.getBean(ConfigService.class);
        }

        String[] split = groupKey.split("\\+");
        ConfigAllInfo config = configService.findConfigAllInfo(split[0], split[1], split[2]);
        if (config == null || StringUtils.isEmpty(config.getTpId())) {
            String errorMessage = String.format("config is null. tpId :: %s, itemId :: %s, tenantId :: %s", split[0], split[1], split[2]);
            throw new RuntimeException(errorMessage);
        }

        return Md5Util.getTpContentMd5(config);
    }

    public static void updateMd5(String groupKey, String ip, String md5) {
        CacheItem cache = makeSure(groupKey, ip);
        if (cache.md5 == null || !cache.md5.equals(md5)) {
            cache.md5 = md5;
            String[] split = groupKey.split("\\+");
            ConfigAllInfo config = configService.findConfigAllInfo(split[0], split[1], split[2]);
            cache.configAllInfo = config;
            cache.lastModifiedTs = System.currentTimeMillis();
            NotifyCenter.publishEvent(new LocalDataChangeEvent(ip, groupKey));
        }
    }

    public synchronized static CacheItem makeSure(String groupKey, String ip) {
        Map<String, CacheItem> ipCacheItemMap = CACHE.get(groupKey);
        CacheItem item = ipCacheItemMap.get(ip);
        if (null != item) {
            return item;
        }

        CacheItem tmp = new CacheItem(groupKey);
        Map<String, CacheItem> cacheItemMap = Maps.newHashMap();
        cacheItemMap.put(ip, tmp);
        CACHE.putIfAbsent(groupKey, cacheItemMap);

        return tmp;
    }

    public static Map<String, CacheItem> getContent(String identification) {
        List<String> identificationList = MapUtil.parseMapForFilter(CACHE, identification);
        Map<String, CacheItem> returnStrCacheItemMap = Maps.newHashMap();
        identificationList.forEach(each -> returnStrCacheItemMap.putAll(CACHE.get(each)));
        return returnStrCacheItemMap;
    }

    /**
     * Remove config cache.
     *
     * @param groupKey 租户 + 项目 + IP
     */
    public synchronized static void removeConfigCache(String groupKey) {
        // 模糊搜索
        List<String> identificationList = MapUtil.parseMapForFilter(CACHE, groupKey);
        for (String cacheMapKey : identificationList) {
            Map<String, CacheItem> removeCacheItem = CACHE.remove(cacheMapKey);
            log.info("Remove invalidated config cache. config info :: {}", JSON.toJSONString(removeCacheItem));
        }
    }

}
