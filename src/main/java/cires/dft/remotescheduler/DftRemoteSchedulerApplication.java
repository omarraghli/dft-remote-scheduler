package cires.dft.remotescheduler;

import cires.dft.remotescheduler.config.PublicHolidayProperties;
import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({RemoteScheduleProperties.class, PublicHolidayProperties.class,
        SecurityProperties.class})
public class DftRemoteSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DftRemoteSchedulerApplication.class, args);
    }

    /** Injected rather than calling {@code now()} directly, so tests can pin the date. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
