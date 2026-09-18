package cires.dft.remotescheduler;

import cires.dft.remotescheduler.scheduler.WeeklyScheduleJob;
import cires.dft.remotescheduler.service.ScheduleService;
import cires.dft.remotescheduler.web.ScheduleRestController;
import cires.dft.remotescheduler.web.ScheduleViewController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApplicationContextTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("the application wires up")
    void contextLoads() {
        assertThat(context.getBean(ScheduleService.class)).isNotNull();
        assertThat(context.getBean(ScheduleRestController.class)).isNotNull();
        assertThat(context.getBean(ScheduleViewController.class)).isNotNull();
    }

    @Test
    @DisplayName("the Thursday job is switched off in tests, so it cannot fire mid-run")
    void jobDisabledInTests() {
        assertThat(context.getBeanNamesForType(WeeklyScheduleJob.class)).isEmpty();
    }
}
