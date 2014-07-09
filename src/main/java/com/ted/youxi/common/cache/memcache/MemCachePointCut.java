package com.ted.youxi.common.cache.memcache;

import javax.annotation.Resource;

import net.spy.memcached.MemcachedClient;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import com.ted.youxi.common.cache.CacheGeterSeter;
import com.ted.youxi.common.cache.CacheWrapper;
import com.ted.youxi.common.cache.annotation.BaiduCacheDeletable;
import com.ted.youxi.common.cache.annotation.BaiduCacheable;
import com.ted.youxi.common.cache.util.CacheUtil;

@Aspect
public class MemCachePointCut implements CacheGeterSeter {

	@Resource
	private MemcachedClient memcachedClient;

	@Pointcut(value = "@annotation(cahce)", argNames = "cahce")
	public void cacheAblePointcut(BaiduCacheable cahce) {
	}

	@Pointcut(value = "@annotation(delCache)", argNames = "delCache")
	public void cacheDelAblePointcut(BaiduCacheDeletable delCache) {
	}

	public void setCache(String cacheKey, Object result, int expire) {
		if (cacheKey == null || cacheKey.trim().length() == 0 || cacheKey == null) {
			return;
		}
		if (cacheKey.length() > 200) {
			cacheKey = CacheUtil.getMiscHashCode(cacheKey);
		}
		try {
			CacheWrapper cacheWrapper = new CacheWrapper();
			cacheWrapper.setCacheObject(result);
			cacheWrapper.setLastLoadTime(System.currentTimeMillis());
			getMemcachedClient().set(cacheKey, expire, cacheWrapper);
		} catch (Exception ex) {
			ex.printStackTrace();
			// logger.error(ex.getMessage(), ex);
		}
	}

	@Around(value = "cacheAblePointcut(cahce)", argNames = "pjp, cahce")
	public Object doCacheArround(ProceedingJoinPoint pjp, BaiduCacheable cache) {
		try {
			// Method m = pjp.getTarget().getClass()
			// .getMethod(pjp.getSignature().getName(), ((MethodSignature)
			// pjp.getSignature()).getMethod().getParameterTypes());
			// Annotation[] anns = m.getDeclaredAnnotations();
			// BaiduCacheable cache = (BaiduCacheable) anns[0];
			return CacheUtil.proceed(pjp, cache, this);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@Around(value = "cacheDelAblePointcut(delCache)", argNames = "pjp, delCache")
	public Object doDelCacheArround(ProceedingJoinPoint pjp, BaiduCacheDeletable delCache) {
		try {
			// Method m = pjp.getTarget().getClass()
			// .getMethod(pjp.getSignature().getName(), ((MethodSignature)
			// pjp.getSignature()).getMethod().getParameterTypes());
			// Annotation[] anns = m.getDeclaredAnnotations();
			// BaiduCacheDeletable cacheDel = (BaiduCacheDeletable) anns[0];
			CacheUtil.proceedDel(pjp, delCache, this);
			return pjp.proceed();
		} catch (Exception e) {
			e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
	}

	public CacheWrapper get(String key) {
		return (CacheWrapper) getMemcachedClient().get(key);
	}

	public MemcachedClient getMemcachedClient() {
		return memcachedClient;
	}

	public void setMemcachedClient(MemcachedClient memcachedClient) {
		this.memcachedClient = memcachedClient;
	}

	@Override
	public void delCache(String cacheKey) {
		getMemcachedClient().delete(cacheKey);
	}

}
