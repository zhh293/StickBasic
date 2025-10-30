package com.tmd.config;



import com.tmd.filter.JwtAuthenticationTokenFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.client.AuthenticationHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;

    @Autowired
    private AuthenticationEntryPoint authenticationHandler;
    @Autowired
    private AccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/login", "/public/login/gitee/config",
                                "/public/login/gitee", "/public/login/ZKP",
                                "/public/login/button", "/home","/ai1/chat","/register").anonymous()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationTokenFilter, UsernamePasswordAuthenticationFilter.class)
                //配置异常处理器
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((AuthenticationEntryPoint) authenticationHandler)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                //允许跨域
                .cors(cors -> {});

        return http.build();
    }

    /*@Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
*/
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 仅在开发环境使用，生产环境绝不允许
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return rawPassword.toString();
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return rawPassword.toString().equals(encodedPassword);
            }
        };
    }

    @Bean
    public AuthenticationManager authenticationManagerBean(HttpSecurity http) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .build();
    }
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return new AuthenticationEntryPoint() {
            @Override
            public void commence(HttpServletRequest request, HttpServletResponse response,
                                 AuthenticationException authException) throws IOException {
                // 自定义认证失败处理逻辑
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\":401,\"message\":\"未认证\"}");
            }
        };
    }

    @Bean
    public org.springframework.security.web.access.AccessDeniedHandler accessDeniedHandler() {
        return new org.springframework.security.web.access.AccessDeniedHandlerImpl() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response,
                               AccessDeniedException accessDeniedException) throws IOException {
                // 自定义权限不足处理逻辑
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\":403,\"message\":\"权限不足\"}");
            }
        };
    }

}
/*
Spring Security 认证配置详解
Spring Security 的认证配置主要通过继承WebSecurityConfigurerAdapter类并重写其方法来实现。下面详细解释几个核心配置点：
        1. 配置 HTTP 请求安全规则
通过configure(HttpSecurity http)方法配置 URL 访问权限、登录 / 登出行为等：

java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .antMatchers("/public/**").permitAll() // 公开访问的路径
                .antMatchers("/admin/**").hasRole("ADMIN") // 需要ADMIN角色
                .anyRequest().authenticated() // 其他请求需要认证
                .and()
                .formLogin()
                .loginPage("/login") // 自定义登录页面
                .defaultSuccessUrl("/home") // 登录成功后跳转
                .permitAll()
                .and()
                .logout()
                .logoutSuccessUrl("/login?logout") // 登出成功后跳转
                .permitAll();
    }
}
2. 配置用户认证方式
可以通过configure(AuthenticationManagerBuilder auth)方法配置内存认证、数据库认证等：

java
@Override
protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth
            .inMemoryAuthentication() // 内存认证
            .withUser("user").password("{noop}password").roles("USER")
            .and()
            .withUser("admin").password("{noop}admin").roles("ADMIN");
}

注意：Spring Security 5 + 要求密码必须使用加密方式存储，{noop}表示明文密码（仅用于测试）。
        3. 密码加密配置
生产环境应使用密码加密：

java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
4. 自定义 UserDetailsService
从数据库获取用户信息：

java
@Autowired
private UserDetailsService userDetailsService;

@Override
protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth
            .userDetailsService(userDetailsService)
            .passwordEncoder(passwordEncoder());
}
5. 跨域 (CORS) 与 CSRF 配置
java
        http
    .cors().and() // 启用CORS
    .csrf().disable(); // 禁用CSRF（仅适用于API服务）
6. 会话管理
        java
http
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) // 会话创建策略
        .invalidSessionUrl("/login") // 会话失效后跳转
        .maximumSessions(1) // 最大会话数
        .expiredUrl("/login?expired"); // 会话过期后跳转
7. 自定义认证成功 / 失败处理器
        java
http
        .formLogin()
        .successHandler(authenticationSuccessHandler())
        .failureHandler(authenticationFailureHandler());
        8. 安全头信息配置
        java
http
        .headers()
        .contentSecurityPolicy("default-src 'self'"); // CSP安全策略
常见配置场景
        前后端分离项目配置
java
        http
    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 无状态会话
    .and()
    .csrf().disable()
    .authorizeRequests()
        .antMatchers(HttpMethod.OPTIONS, "/**").permitAll() // 允许OPTIONS请求
        .anyRequest().authenticated()
    .and()
    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class); // JWT过滤器
OAuth2 登录配置
java
        http
    .oauth2Login()
        .loginPage("/login")
        .userInfoEndpoint()
            .userService(customOAuth2UserService) // 自定义OAuth2用户服务
        .and()
        .successHandler(oauth2AuthenticationSuccessHandler); // 认证成功处理器*/

