package com.ted.youxi.common.cache;

import java.io.Serializable;

public class CacheWrapper implements Serializable {
	private static final long serialVersionUID = 1L;

	private Object cacheObject;

	private long lastLoadTime;

	public CacheWrapper() {
	}

	public CacheWrapper(Object cacheObject) {
		this.cacheObject = cacheObject;
		this.lastLoadTime = System.currentTimeMillis();
	}

	public long getLastLoadTime() {
		return lastLoadTime;
	}

	public void setLastLoadTime(long lastLoadTime) {
		this.lastLoadTime = lastLoadTime;
	}

	public Object getCacheObject() {
		return cacheObject;
	}

	public void setCacheObject(Object cacheObject) {
		this.cacheObject = cacheObject;
	}

}
