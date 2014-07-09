package com.ted.youxi.common.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BaiduCacheable {

	/**
	 * 超时时间
	 * 
	 * @return
	 */
	int expire();

	/**
	 * 该对象所属的domain
	 * 
	 * @return
	 */
	String domain() default "";

	boolean emptyTOPersist() default true;

	String keyPrex() default "";
}
