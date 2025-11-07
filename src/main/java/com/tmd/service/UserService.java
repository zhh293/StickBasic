package com.tmd.service;


import com.tmd.entity.dto.UserProfile;
import com.tmd.entity.dto.UserUpdateDTO;
import com.tmd.entity.po.UserData;
import org.springframework.stereotype.Service;

@Service
public interface UserService {

    public boolean register(UserData userData);

    public UserData login(UserData userData);

    UserProfile getProfile(Long userId);

    boolean updatePassword(long uid, String oldPassword, String newPassword);

    void updateUserProfile(Long id, UserUpdateDTO userUpdateDTO);
}
