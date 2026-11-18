package com.quan.reportservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "app.jwt.secret=test-only-dummy-secret-key-at-least-32-bytes!!")
class ReportServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
