package com.multimart.modules.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multimart.modules.user.dto.LoginRequest;
import com.multimart.modules.user.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("End-to-End: Đăng ký -> Đăng nhập lấy JWT -> Gọi API /me thành công")
    void testAuthFlow_Success() throws Exception {
        // 1. Đăng ký tài khoản mới
        RegisterRequest registerReq = RegisterRequest.builder()
                .email("trader_test@gmail.com")
                .password("password123")
                .fullName("Nguyen Van A")
                .phone("0987654321")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is(1000)))
                .andExpect(jsonPath("$.data.email", is("trader_test@gmail.com")))
                .andExpect(jsonPath("$.data.fullName", is("Nguyen Van A")));

        // 2. Đăng nhập để nhận JWT Access Token
        LoginRequest loginReq = LoginRequest.builder()
                .email("trader_test@gmail.com")
                .password("password123")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(1000)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andReturn();

        // Trích xuất accessToken từ JSON response
        String responseBody = loginResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseBody)
                .path("data")
                .path("accessToken")
                .asText();

        // 3. Gọi API /me với Bearer Token -> Phải thành công
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(1000)))
                .andExpect(jsonPath("$.data.email", is("trader_test@gmail.com")));

        // 4. Gọi API /me KHÔNG kèm Token -> Bị chặn 401 Unauthorized
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is(2001)));
    }

    @Test
    @DisplayName("Đăng ký trùng email -> Phải trả về lỗi 409 USER_EXISTED")
    void testRegister_DuplicateEmail_Fails() throws Exception {
        RegisterRequest req1 = RegisterRequest.builder()
                .email("duplicate@gmail.com")
                .password("password123")
                .fullName("User One")
                .phone("0123456789")
                .build();

        // Lần 1: Thành công
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Lần 2: Trùng email -> Lỗi 409
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(2004))); // ErrorCode.USER_EXISTED code
    }
}
