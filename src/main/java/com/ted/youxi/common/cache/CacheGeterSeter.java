package com.ted.youxi.common.cache;

public interface CacheGeterSeter {
	void setCache(String cacheKey, Object result, int expire);

	CacheWrapper get(String key);

	void delCache(String cacheKey);
}
