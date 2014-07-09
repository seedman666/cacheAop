package com.ted.youxi.common.cache.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;

import com.ted.youxi.common.cache.CacheGeterSeter;
import com.ted.youxi.common.cache.CacheWrapper;
import com.ted.youxi.common.cache.annotation.BaiduCacheDeletable;
import com.ted.youxi.common.cache.annotation.BaiduCacheKey;
import com.ted.youxi.common.cache.annotation.BaiduCacheable;

public final class CacheUtil {
	private static final Logger LOGGER = Logger.getLogger(CacheUtil.class);

	private static final String SPLIT_STR = "_";

	private static final Map<String, Long> PROCESSLIST = new ConcurrentHashMap<String, Long>();

	private static final ReentrantLock LOCK = new ReentrantLock();

	private static final BeanUtil BEANUTIL = BeanUtil.getInstance();

	private static final String KEY_DOMAIN_PREF = "com.baidu.youxi.common.cache.util.";

	private static final String VERSION = "1.0";

	private CacheUtil() {

	}

	/**
	 * 生成字符串的HashCode
	 * 
	 * @param buf
	 * @return int
	 * @author liuliulong
	 * @Date 2014年5月13日
	 */
	private static int getHashCode(String buf) {
		int hash = 5381;
		int len = buf.length();

		while (len-- > 0) {
			hash = ((hash << 5) + hash) + buf.charAt(len); /* hash * 33 + c */
		}
		return hash;
	}

	public static String getCacheKey(String className, String method, Object[] arguments) {
		StringBuilder sb = new StringBuilder();
		sb.append(VERSION).append(className).append(".").append(method).append(":");
		if (null != arguments && arguments.length > 0) {
			StringBuilder arg = new StringBuilder();
			for (Object obj : arguments) {
				arg.append(BEANUTIL.transforToString(obj));
			}
			sb.append(getMiscHashCode(arg.toString()));
		}
		return sb.toString();
	}

	public static String getMiscHashCode(String str) {
		if (null == str || str.length() == 0) {
			return "";
		}
		StringBuilder tmp = new StringBuilder();
		tmp.append(str.hashCode()).append(SPLIT_STR).append(getHashCode(str));
		if (str.length() >= 2) {
			int mid = str.length() / 2;
			String str1 = str.substring(0, mid);
			String str2 = str.substring(mid);
			tmp.append(SPLIT_STR).append(str1.hashCode());
			tmp.append(SPLIT_STR).append(str2.hashCode());
		}
		return tmp.toString();
	}

	public static Object proceed(ProceedingJoinPoint pjp, BaiduCacheable cache, CacheGeterSeter cacheGeterSeter)
			throws Exception {
		Object[] arguments = pjp.getArgs();
		int expire = cache.expire();
		if (expire <= 0) {
			expire = 300;
		}
		int updateTime = (expire * 2) / 3; // 更新时间
		String cacheKey = getCacheKey(pjp.getTarget().getClass().getName(), pjp.getSignature().getName(), arguments);
		LOGGER.info("====================cacheKey:" + cacheKey);
		CacheWrapper cacheWrapper = cacheGeterSeter.get(cacheKey);
		LOGGER.info("====================cacheVal:" + cacheWrapper);
		if (null != cacheWrapper) {
			if ((System.currentTimeMillis() - cacheWrapper.getLastLoadTime()) / 1000 >= updateTime) {
				Long lastProcTime = null;
				try {
					LOCK.lock();
					lastProcTime = PROCESSLIST.get(cacheKey);// 为发减少数据层的并发，增加等待机制。
					if (null == lastProcTime) {
						PROCESSLIST.put(cacheKey, System.currentTimeMillis());
					}
				} finally {
					LOCK.unlock();
				}
				if (null == lastProcTime) {
					new Thread(new AsyncLoadData(pjp, cacheKey, cacheGeterSeter, cache)).start();
				}
			}
			return cacheWrapper.getCacheObject();
		} else {
			return loadData(pjp, cacheKey, cacheGeterSeter, cache);
		}
	}

	private static Object loadData(ProceedingJoinPoint pjp, String cacheKey, CacheGeterSeter cacheGeterSeter,
			BaiduCacheable cache) throws Exception {
		Object result = null;
		try {
			long startTime = System.currentTimeMillis();
			result = pjp.proceed();
			LOGGER.info("============result:" + result);
			long useTime = System.currentTimeMillis() - startTime;
			if (useTime >= 500) {
				String className = pjp.getTarget().getClass().getName();
				LOGGER.error(className + "." + pjp.getSignature().getName() + "use time:" + useTime + "ms");
			}
			boolean isAddCache = false;
			if (checkIsEmpty(result)) {
				if (cache.emptyTOPersist()) {
					isAddCache = true;
					cacheGeterSeter.setCache(cacheKey, result, cache.expire());
				}
			} else {
				isAddCache = true;
				cacheGeterSeter.setCache(cacheKey, result, cache.expire());
			}
			if (isAddCache) {
				if (cache.domain() != null && cache.domain().trim().length() > 0) {
					new Thread(new AsyncAddDomainCacheKey(cacheKey, cache.domain(), cacheGeterSeter, cache.expire()))
							.start();
				}
				String subDomain = getSubDomain(pjp.getArgs());
				if (subDomain != null && subDomain.trim().length() > 0) {
					new Thread(new AsyncAddDomainCacheKey(cacheKey, cache.keyPrex() + subDomain, cacheGeterSeter,
							cache.expire())).start();
				}
			}
		} catch (Exception e) {
			throw e;
		} catch (Throwable e) {
			throw new Exception(e);
		} finally {
			synchronized (LOCK) {
				LOCK.notifyAll();
			}
			PROCESSLIST.remove(cacheKey);
		}
		return result;
	}

	@SuppressWarnings("rawtypes")
	private static String getSubDomain(Object... objects) {
		StringBuffer sb = new StringBuffer();
		if (objects == null || objects.length == 0) {
			return null;
		}
		for (Object obj : objects) {
			Class cl = obj.getClass();
			if (cl.isPrimitive()) {
				continue;
			}
			Field[] fields = cl.getDeclaredFields();
			if (fields == null || fields.length == 0) {
				continue;
			}
			for (Field field : fields) {
				Annotation[] annos = field.getDeclaredAnnotations();
				if (annos == null || annos.length == 0) {
					continue;
				}
				for (Annotation anno : annos) {
					boolean isBaiduCacheKeyAnno = true;
					try {
						@SuppressWarnings("unused")
						BaiduCacheKey key = (BaiduCacheKey) anno;
					} catch (Exception ex) {
						isBaiduCacheKeyAnno = false;
					}
					if (isBaiduCacheKeyAnno) {
						try {
							field.setAccessible(true);
							sb.append(field.getName()).append(SPLIT_STR).append(field.get(obj)).append(SPLIT_STR);
						} catch (IllegalArgumentException e) {
							e.printStackTrace();
						} catch (IllegalAccessException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
		return sb.toString();
	}

	@SuppressWarnings("rawtypes")
	private static boolean checkIsEmpty(Object result) {
		if (result == null) {
			return true;
		}
		if (result instanceof Collection) {
			Collection col = (Collection) result;
			return col.isEmpty();
		}
		return false;
	}

	private static class AsyncLoadData implements Runnable {
		private ProceedingJoinPoint pjp;
		private String cacheKey;
		private CacheGeterSeter cacheGeterSeter;
		private BaiduCacheable cache;

		public AsyncLoadData(ProceedingJoinPoint pjp, String cacheKey, CacheGeterSeter cacheGeterSeter,
				BaiduCacheable cache) {
			this.pjp = pjp;
			this.cacheKey = cacheKey;
			this.cacheGeterSeter = cacheGeterSeter;
			this.cache = cache;
		}

		public void run() {
			try {
				loadData(pjp, cacheKey, cacheGeterSeter, cache);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	private static class AsyncAddDomainCacheKey implements Runnable {
		private String key;

		private String domain;

		private CacheGeterSeter cacheGeterSeter;

		private int expire;

		public AsyncAddDomainCacheKey(String key, String domain, CacheGeterSeter cacheGeterSeter, int expire) {
			this.key = key;
			this.domain = domain;
			this.cacheGeterSeter = cacheGeterSeter;
			this.expire = expire;
		}

		@SuppressWarnings("unchecked")
		@Override
		public void run() {
			CacheWrapper keyWrapper = cacheGeterSeter.get(KEY_DOMAIN_PREF + domain);
			Set<String> keys = new HashSet<String>();
			if (keyWrapper != null) {
				keys = (Set<String>) keyWrapper.getCacheObject();
			}
			keys.add(key);
			LOGGER.info("================add domain key:" + KEY_DOMAIN_PREF + domain + "," + keys);
			cacheGeterSeter.setCache(KEY_DOMAIN_PREF + domain, keys, expire);
		}

	}

	public static void proceedDel(ProceedingJoinPoint pjp, BaiduCacheDeletable cacheDel, CacheGeterSeter cacheGeterSeter) {
		Object[] arguments = pjp.getArgs();
		String cacheKey = getCacheKey(pjp.getTarget().getClass().getName(), pjp.getSignature().getName(), arguments);
		CacheWrapper cacheWrapper = cacheGeterSeter.get(cacheKey);
		if (cacheWrapper != null) {
			cacheGeterSeter.delCache(cacheKey);
			LOGGER.info("==================DelCacheWithParamOK:" + cacheKey);
		}
		if (cacheDel.domain() != null && cacheDel.domain().trim().length() > 0) {
			new Thread(new AsyncDelCache(cacheDel.domain(), cacheGeterSeter)).start();
		}
		String subDomain = getSubDomain(arguments);
		if (subDomain != null && subDomain.trim().length() > 0) {
			new Thread(new AsyncDelCache(cacheDel.keyPrex() + subDomain, cacheGeterSeter)).start();
		}
	}

	private static class AsyncDelCache implements Runnable {
		private String domain;

		private CacheGeterSeter cacheGeterSeter;

		public AsyncDelCache(String domain, CacheGeterSeter cacheGeterSeter) {
			this.domain = domain;
			this.cacheGeterSeter = cacheGeterSeter;
		}

		@SuppressWarnings("unchecked")
		@Override
		public void run() {
			CacheWrapper keyWrapper = cacheGeterSeter.get(KEY_DOMAIN_PREF + domain);
			if (keyWrapper != null) {
				Set<String> keys = (Set<String>) keyWrapper.getCacheObject();
				if (keys != null && keys.size() > 0) {
					for (String key : keys) {
						cacheGeterSeter.delCache(key);
					}
				}
				LOGGER.info("=====================del domain key:" + KEY_DOMAIN_PREF + domain + "," + keys);
				cacheGeterSeter.delCache(KEY_DOMAIN_PREF + domain);
			}
		}
	}
}
