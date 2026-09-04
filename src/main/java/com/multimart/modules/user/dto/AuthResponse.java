package com.multimart.modules.user.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    @Builder.Default
    private String tokenType = "Bearer";

    private String accessToken;
    private String refreshToken;
    private UserResponse user;
}
