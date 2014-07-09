package com.ted.youxi.common.cache.util;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class BeanUtil {

	private static BeanUtil instance;

	private static Object object = new Object();

	private BeanUtil() {

	}

	public static BeanUtil getInstance() {
		if (instance == null) {
			synchronized (object) {
				if (instance == null) {
					instance = new BeanUtil();
				}
			}
		}
		return instance;
	}

	@SuppressWarnings("rawtypes")
	public String transforToString(Object obj) {
		if (obj == null) {
			return "null";
		}
		Class cl = obj.getClass();
		if (obj.getClass().isPrimitive() || obj instanceof String || obj instanceof Integer || obj instanceof Long
				|| obj instanceof Byte || obj instanceof Character || obj instanceof Boolean || obj instanceof Short
				|| obj instanceof Float || obj instanceof Double || obj instanceof Date) {
			return String.valueOf(obj);
		} else if (obj instanceof Enum) {
			return ((Enum) obj).name();
		} else if (cl.isArray()) {
			String r = "[";
			for (int i = 0; i < Array.getLength(obj); i++) {
				if (i > 0) {
					r += ",";
				}
				Object val = Array.get(obj, i);
				if (null == val) {
					r += "null";
				} else if (val.getClass().isPrimitive()) {
					r += val;
				} else {
					r += transforToString(val);
				}
			}
			return r + "]";
		} else if (obj instanceof List) {
			List tempList = (List) obj;
			String r = "[";
			for (int i = 0; i < tempList.size(); i++) {
				if (i > 0) {
					r += ",";
				}
				Object val = tempList.get(i);
				if (null == val) {
					r += "null";
				} else if (val.getClass().isPrimitive()) {
					r += val;
				} else {
					r += transforToString(val);
				}
			}
			return r + "]";
		} else if (obj instanceof Map) {
			Map tempMap = (Map) obj;
			String r = "{";
			Iterator it = tempMap.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry entry = (Entry) it.next();
				if (it.hasNext()) {
					r += ",";
				}
				Object key = entry.getKey();
				if (null == key) {
					r += "null";
				} else if (key.getClass().isPrimitive()) {
					r += key;
				} else {
					r += transforToString(key);
				}
				r += "=";
				Object val = entry.getValue();
				if (null == val) {
					r += "null";
				} else if (val.getClass().isPrimitive()) {
					r += val;
				} else {
					r += transforToString(val);
				}
			}
			return r + "}";
		}
		String r = cl.getName();
		do {
			Field[] fields = cl.getDeclaredFields();
			AccessibleObject.setAccessible(fields, true);
			if (null == fields || fields.length == 0) {
				cl = cl.getSuperclass();
				continue;
			}
			r += "[";
			// get the names and values of all fields
			for (Field f : fields) {
				if (!Modifier.isStatic(f.getModifiers())) {
					r += f.getName() + "=";
					try {
						Class t = f.getType();
						Object val = f.get(obj);
						if (t.isPrimitive()) {
							r += val;
						} else {
							r += transforToString(val);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					r += ",";
				}
			}
			if (r.endsWith(",")) {
				r = r.substring(0, r.length() - 1);
			}
			r += "]";
			cl = cl.getSuperclass();
		} while (cl != null);
		return r;
	}

}
