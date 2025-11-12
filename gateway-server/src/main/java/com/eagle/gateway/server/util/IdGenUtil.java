package com.eagle.gateway.server.util;

import cn.hutool.core.lang.UUID;


public class IdGenUtil {

	public static String uuid() {
		//雪花算法
		return UUID.fastUUID().toString().replace("-", "");
	}
}
