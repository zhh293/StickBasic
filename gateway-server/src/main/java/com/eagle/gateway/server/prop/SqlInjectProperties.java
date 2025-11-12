package com.eagle.gateway.server.prop;

import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.sql-inject")
@Data
public class SqlInjectProperties {
	// 正则表达式（从配置文件注入）
	private String regex;
	// 编译后的Pattern（单例缓存，线程安全）
	private Pattern sqlPattern;

	// 初始化Pattern（确保regex注入后执行）
	@PostConstruct
	public void initPattern() {
		if (StringUtils.isBlank(regex)) {
			throw new IllegalArgumentException("SQL注入检测正则表达式未配置");
		}
		this.sqlPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
	}

	// 提供匹配方法（实例方法，避免静态问题）
	public boolean match(String content) {
		return sqlPattern.matcher(content).find();
	}
}
