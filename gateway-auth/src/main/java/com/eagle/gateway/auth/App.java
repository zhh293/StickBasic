package com.eagle.gateway.auth;

import java.util.HashMap;
import java.util.Map;

import com.eagle.gateway.auth.Service.UserServiceImpl;
import com.eagle.gateway.auth.domain.User;
import com.eagle.gateway.auth.handle.Result;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
@EnableResourceServer
@MapperScan("com.eagle.gateway.auth.mapper")
public class App {

	@Autowired
	private UserServiceImpl userService;

	@RequestMapping(value = "/user")
	public Map<String, Object> user(OAuth2Authentication user) {
		Map<String, Object> userInfo = new HashMap<>();
		userInfo.put("user", user.getUserAuthentication().getPrincipal());
		userInfo.put("authorities", AuthorityUtils.authorityListToSet(user.getUserAuthentication().getAuthorities()));
		return userInfo;
	}

	@PostMapping("/login")
	public Result login(@RequestBody User userDto) {
		return userService.login(userDto);
	}

	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}
}
