package com.udap.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.udap.employee.domain.Employee;
import com.udap.employee.dto.EmployeeRequest;
import com.udap.employee.dto.EmployeeResponse;
import com.udap.employee.repository.EmployeeRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository repository;

    @InjectMocks
    private EmployeeService service;

    private Employee stored;

    @BeforeEach
    void setUp() {
        stored = new Employee();
        stored.setId(1L);
        stored.setFirstName("Ada");
        stored.setLastName("Lovelace");
        stored.setEmail("ada@example.com");
        stored.setDepartment("Engineering");
        stored.setPosition("Principal Engineer");
        stored.setSalary(new BigDecimal("185000.00"));
        stored.setHireDate(LocalDate.of(2021, 3, 1));
        stored.setCreatedAt(Instant.parse("2021-03-01T00:00:00Z"));
        stored.setUpdatedAt(Instant.parse("2021-03-01T00:00:00Z"));
    }

    private EmployeeRequest request(final String email) {
        return new EmployeeRequest(
                " Ada ", " Lovelace ", email, " Engineering ", " Principal Engineer ",
                new BigDecimal("185000.00"), LocalDate.of(2021, 3, 1));
    }

    @Test
    void listReturnsAllWhenDepartmentIsNull() {
        final Pageable pageable = PageRequest.of(0, 20);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(stored)));

        final var page = service.list(null, pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).email()).isEqualTo("ada@example.com");
    }

    @Test
    void listReturnsAllWhenDepartmentIsBlank() {
        final Pageable pageable = PageRequest.of(0, 20);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(stored)));

        assertThat(service.list("  ", pageable).getTotalElements()).isEqualTo(1);
    }

    @Test
    void listFiltersByDepartment() {
        final Pageable pageable = PageRequest.of(0, 20);
        when(repository.findByDepartmentIgnoreCase("Engineering", pageable))
                .thenReturn(new PageImpl<>(List.of(stored)));

        assertThat(service.list("Engineering", pageable).getContent()).hasSize(1);
    }

    @Test
    void getReturnsEmployee() {
        when(repository.findById(1L)).thenReturn(Optional.of(stored));

        final EmployeeResponse response = service.get(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.lastName()).isEqualTo("Lovelace");
        assertThat(response.department()).isEqualTo("Engineering");
        assertThat(response.position()).isEqualTo("Principal Engineer");
        assertThat(response.salary()).isEqualByComparingTo("185000.00");
        assertThat(response.hireDate()).isEqualTo(LocalDate.of(2021, 3, 1));
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void getThrowsWhenMissing() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(42L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void createNormalisesAndPersists() {
        when(repository.existsByEmail("New@Example.com")).thenReturn(false);
        when(repository.save(any(Employee.class))).thenAnswer(inv -> {
            final Employee e = inv.getArgument(0);
            e.setId(7L);
            return e;
        });

        final EmployeeResponse response = service.create(request("New@Example.com"));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(repository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("ada@example.com")))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("ada@example.com");
        verify(repository, never()).save(any(Employee.class));
    }

    @Test
    void updateAppliesChanges() {
        when(repository.findById(1L)).thenReturn(Optional.of(stored));
        when(repository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        final EmployeeResponse response = service.update(1L, request("ada@example.com"));

        assertThat(response.position()).isEqualTo("Principal Engineer");
        assertThat(response.email()).isEqualTo("ada@example.com");
    }

    @Test
    void updateAllowsChangingToFreeEmail() {
        when(repository.findById(1L)).thenReturn(Optional.of(stored));
        when(repository.existsByEmail("fresh@example.com")).thenReturn(false);
        when(repository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(1L, request("fresh@example.com")).email())
                .isEqualTo("fresh@example.com");
    }

    @Test
    void updateRejectsEmailTakenByAnother() {
        when(repository.findById(1L)).thenReturn(Optional.of(stored));
        when(repository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, request("taken@example.com")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void updateThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request("x@example.com")))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void deleteRemovesExisting() {
        when(repository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(repository.existsById(5L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(5L))
                .isInstanceOf(EmployeeNotFoundException.class);
        verify(repository, never()).deleteById(5L);
    }
}
