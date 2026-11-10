package com.sky.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理端端到端集成测试（真实 HTTP + 真实 MySQL/Redis）
 *
 * <p>与单元测试（Mockito mock 依赖）不同，本测试通过 {@code TestRestTemplate} 发起真实
 * HTTP 请求，走完整的 Spring MVC 链路：拦截器认证 → Controller → Service → Mapper → 数据库，
 * 以及 Redis 缓存、BCrypt 密码校验、JWT 生成与校验，验证各层协作是否正确。</p>
 *
 * <p>前置条件：本机 MySQL、Redis 已启动（当前环境均已在线）。</p>
 *
 * <p>测试顺序用 {@code @Order} 控制，登录拿到 token 后供后续受保护接口复用。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("管理端端到端集成测试")
class AdminLoginIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /** 登录成功后缓存的 token，供受保护接口复用 */
    private static String adminToken;

    // ==================== 登录模块 ====================

    @Test
    @Order(1)
    @DisplayName("登录：正确账号密码 → 返回 token")
    void login_success() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("username", "admin");
        body.put("password", "123456");

        ResponseEntity<String> resp = post("/admin/employee/login", body, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode(), "登录接口应返回 200");
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertEquals(1, json.get("code").asInt(), "登录应返回 code=1（成功）");

        JsonNode data = json.get("data");
        assertNotNull(data, "返回体应包含 data");
        assertNotNull(data.get("token"), "应返回 JWT token");
        assertEquals("admin", data.get("userName").asText(), "用户名应为 admin");
        assertEquals("管理员", data.get("name").asText(), "姓名应为 管理员");

        adminToken = data.get("token").asText();
        assertFalse(adminToken.isEmpty(), "token 不能为空");
        System.out.println("[集成测试] 登录成功，token=" + adminToken.substring(0, 20) + "...");
    }

    @Test
    @Order(2)
    @DisplayName("登录：错误密码 → 返回失败")
    void login_wrongPassword() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("username", "admin");
        body.put("password", "wrong-password-xxx");

        ResponseEntity<String> resp = post("/admin/employee/login", body, null);
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertEquals(0, json.get("code").asInt(), "错误密码应返回 code=0（失败）");
    }

    @Test
    @Order(3)
    @DisplayName("登录：空用户名 → 参数校验失败")
    void login_emptyUsername() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("username", "");
        body.put("password", "123456");

        ResponseEntity<String> resp = post("/admin/employee/login", body, null);
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertEquals(0, json.get("code").asInt(), "空用户名应触发参数校验失败");
    }

    // ==================== 认证链路 ====================

    @Test
    @Order(4)
    @DisplayName("受保护接口：无 token → 被拦截(401)")
    void protectedApi_withoutToken() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/admin/employee/page", String.class);
        // 拦截器返回 401
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode(), "无 token 访问受保护接口应返回 401");
    }

    @Test
    @Order(5)
    @DisplayName("受保护接口：带合法 token → 正常返回(200)")
    void protectedApi_withValidToken() {
        assertNotNull(adminToken, "前置登录应已拿到 token");
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", adminToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 员工分页查询接口（GET /admin/employee/page?page=1&pageSize=10）
        ResponseEntity<String> resp = restTemplate.exchange(
                "/admin/employee/page?page=1&pageSize=10",
                HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode(), "带合法 token 访问应返回 200");
    }

    @Test
    @Order(6)
    @DisplayName("受保护接口：伪造 token → 被拦截(401)")
    void protectedApi_withInvalidToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", "fake.invalid.token.xxx");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> resp = restTemplate.exchange(
                "/admin/employee/page?page=1&pageSize=10",
                HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode(), "伪造 token 应返回 401");
    }

    // ==================== 业务接口（真实 DB + Redis）====================

    @Test
    @Order(7)
    @DisplayName("分类查询：带 token → 返回分类列表")
    void categoryList_withToken() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", adminToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> resp = restTemplate.exchange(
                "/admin/category/list?type=1",
                HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertEquals(1, json.get("code").asInt(), "分类查询应返回 code=1");
    }

    // ==================== 工具方法 ====================

    private ResponseEntity<String> post(String path, Object body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("token", token);
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(path, entity, String.class);
    }
}
