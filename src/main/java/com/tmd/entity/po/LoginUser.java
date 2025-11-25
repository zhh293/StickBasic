package com.tmd.entity.po;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Data

@NoArgsConstructor
public class LoginUser implements UserDetails {
    private UserData user;
    //存储权限信息
    private List<String> permissions;
    //存储SpringSecurity所需要的权限信息
    @JSONField(serialize = false)
    private Set<SimpleGrantedAuthority> authorities;
    public LoginUser(UserData user, List<String> permissions) {
        this.user = user;
        this.permissions = permissions;
    }
    public LoginUser(UserData user){
        this.user = user;
    }
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        //把permissions封装成GrantedAuthority
        /*for(String permission : permissions){
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority(permission);
            authorities.add(authority);
        }*/
        if(authorities!= null){
            return authorities;
        }
        if(permissions!= null) {
            authorities = permissions.stream()
                    .map(new Function<String, SimpleGrantedAuthority>() {
                        @Override
                        public SimpleGrantedAuthority apply(String permission) {
                            return new SimpleGrantedAuthority(permission);
                        }
                    })
                    .collect(Collectors.toSet());
        }
        log.info("authorities:{}",authorities);
        log.info("哈哈哈，我来了");
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
