package com.lreyes.platform;

import com.lreyes.platform.core.events.OutboxScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestPlatformJdbcConfig.class)
class OutboxSchedulerIT {

    @Autowired
    private OutboxScheduler outboxScheduler;

    @Test
    void schedulerBeanIsPresent() {
        assertThat(outboxScheduler).isNotNull();
    }

    @Test
    void processOutbox_doesNotThrowWithEmptyOutbox() {
        assertThatCode(() -> outboxScheduler.processOutbox())
                .doesNotThrowAnyException();
    }
}
