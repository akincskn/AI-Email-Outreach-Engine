package com.akincoskun.outreach;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
@Tag("integration")
class OutreachApplicationTest {

    @Test
    void contextLoads() {
    }
}
