package io.github.francisco3ferraz.liftlog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LiftLogApplicationTests {

    @Value("${spring.application.name}")
    String applicationName;

    @Value("${server.shutdown}")
    String shutdown;

    @Value("${spring.threads.virtual.enabled}")
    boolean virtualThreads;

    @Test
    void contextLoadsWithBaseConfiguration() {
        assertThat(applicationName).isEqualTo("liftlog");
        assertThat(shutdown).isEqualTo("graceful");
        assertThat(virtualThreads).isTrue();
    }
}
