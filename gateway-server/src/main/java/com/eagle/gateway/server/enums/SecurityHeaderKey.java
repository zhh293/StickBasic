package com.eagle.gateway.server.enums;

import lombok.AllArgsConstructor;


@AllArgsConstructor
public enum SecurityHeaderKey {

	ENCRYPT("encrypt"),

	REQID("reqid"),

	REQTIME("reqtime");

	private static final String BASE = "x-ca-";

	private String name;

	public String value() {
		return BASE + this.name;
	}

	@Override
	public String toString() {
		return this.value();
	}
}
