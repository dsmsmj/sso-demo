package com.example.iam.dto;

import com.example.iam.entity.OaUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** OA 用户的 session 载体，必须 Serializable 才能存进 HttpSession。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OaUserDto implements Serializable {
    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String department;
    private String externalId;

    public static OaUserDto from(OaUser u) {
        return new OaUserDto(u.getId(), u.getUsername(),
                u.getDisplayName(), u.getEmail(), u.getDepartment(), u.getExternalId());
    }
}
