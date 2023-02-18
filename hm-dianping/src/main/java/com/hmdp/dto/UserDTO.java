package com.hmdp.dto;

import com.hmdp.entity.User;
import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
