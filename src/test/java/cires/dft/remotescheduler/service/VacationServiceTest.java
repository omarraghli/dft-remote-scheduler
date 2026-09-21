package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.domain.Vacation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "remote.job.enabled=false")
class VacationServiceTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private VacationService vacations;

    @Test
    @DisplayName("leave can be added, edited and removed")
    void fullLifecycle() {
        Vacation added = vacations.add("  sara  ",
                LocalDate.of(2029, 7, 9), LocalDate.of(2029, 7, 20));

        assertThat(added.getId()).isNotNull();
        // Matched against the roster, so it is stored spelled the way the chart spells it.
        assertThat(added.getPersonName()).isEqualTo("Sara");
        assertThat(added.days()).isEqualTo(12);

        vacations.update(added.getId(), "Sara",
                LocalDate.of(2029, 7, 9), LocalDate.of(2029, 7, 13));
        assertThat(vacations.require(added.getId()).days()).isEqualTo(5);

        vacations.delete(added.getId());
        assertThatThrownBy(() -> vacations.require(added.getId()))
                .isInstanceOf(VacationManagementException.class);
    }

    @Test
    @DisplayName("a single day off is a stretch of one day")
    void oneDayOff() {
        Vacation added = vacations.add("Omar",
                LocalDate.of(2029, 8, 6), LocalDate.of(2029, 8, 6));

        try {
            assertThat(added.days()).isEqualTo(1);
            assertThat(added.covers(LocalDate.of(2029, 8, 6))).isTrue();
            assertThat(added.covers(LocalDate.of(2029, 8, 7))).isFalse();

        } finally {
            vacations.delete(added.getId());
        }
    }

    @Test
    @DisplayName("leave that ends before it starts, or belongs to nobody, is refused")
    void validatesTheEntry() {
        assertThatThrownBy(() -> vacations.add("Sara",
                LocalDate.of(2029, 9, 10), LocalDate.of(2029, 9, 3)))
                .isInstanceOf(VacationManagementException.class)
                .hasMessageContaining("before it starts");

        assertThatThrownBy(() -> vacations.add("Nobody",
                LocalDate.of(2029, 9, 3), LocalDate.of(2029, 9, 10)))
                .isInstanceOf(VacationManagementException.class)
                .hasMessageContaining("roster");

        assertThatThrownBy(() -> vacations.add("Sara",
                LocalDate.of(2029, 9, 3), LocalDate.of(2030, 9, 3)))
                .isInstanceOf(VacationManagementException.class)
                .hasMessageContaining("at most");
    }

    @Test
    @DisplayName("the same person cannot be away twice over the same days")
    void refusesAnOverlapForTheSamePerson() {
        Vacation first = vacations.add("Adnan",
                LocalDate.of(2029, 10, 1), LocalDate.of(2029, 10, 12));

        try {
            assertThatThrownBy(() -> vacations.add("Adnan",
                    LocalDate.of(2029, 10, 10), LocalDate.of(2029, 10, 15)))
                    .isInstanceOf(VacationManagementException.class)
                    .hasMessageContaining("already away");

            // Somebody else over the same days is perfectly ordinary.
            Vacation other = vacations.add("Anass",
                    LocalDate.of(2029, 10, 10), LocalDate.of(2029, 10, 15));
            vacations.delete(other.getId());

            // And editing an entry does not collide with itself.
            vacations.update(first.getId(), "Adnan",
                    LocalDate.of(2029, 10, 1), LocalDate.of(2029, 10, 19));

        } finally {
            vacations.delete(first.getId());
        }
    }
}
