package com.eagle.gateway.server.exception;

import com.eagle.gateway.server.enums.ServerErrorCode;

import lombok.Getter;


@Getter
public class ServerException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final ServerErrorCode error;

	private final String detail;

	public ServerException(ServerErrorCode error) {
		this(error, "");
	}

	public ServerException(ServerErrorCode error, String detail) {
		this.error = error;
		this.detail = detail;
	}

}
