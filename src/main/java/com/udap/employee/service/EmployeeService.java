package com.udap.employee.service;

import com.udap.employee.domain.Employee;
import com.udap.employee.dto.EmployeeRequest;
import com.udap.employee.dto.EmployeeResponse;
import com.udap.employee.repository.EmployeeRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business rules for employee records. */
@Service
public class EmployeeService {

    private final EmployeeRepository repository;

    public EmployeeService(final EmployeeRepository employeeRepository) {
        this.repository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> list(final String department, final Pageable pageable) {
        final Page<Employee> page;
        if (department == null || department.isBlank()) {
            page = repository.findAll(pageable);
        } else {
            page = repository.findByDepartmentIgnoreCase(department, pageable);
        }
        return page.map(EmployeeResponse::from);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(final Long id) {
        return repository.findById(id)
                .map(EmployeeResponse::from)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    @Transactional
    public EmployeeResponse create(final EmployeeRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }
        final Employee employee = new Employee();
        apply(employee, request);
        final Instant now = Instant.now();
        employee.setCreatedAt(now);
        employee.setUpdatedAt(now);
        return EmployeeResponse.from(repository.save(employee));
    }

    @Transactional
    public EmployeeResponse update(final Long id, final EmployeeRequest request) {
        final Employee employee = repository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        if (!employee.getEmail().equalsIgnoreCase(request.email())
                && repository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }
        apply(employee, request);
        employee.setUpdatedAt(Instant.now());
        return EmployeeResponse.from(repository.save(employee));
    }

    @Transactional
    public void delete(final Long id) {
        if (!repository.existsById(id)) {
            throw new EmployeeNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private void apply(final Employee employee, final EmployeeRequest request) {
        employee.setFirstName(request.firstName().trim());
        employee.setLastName(request.lastName().trim());
        employee.setEmail(request.email().trim().toLowerCase(java.util.Locale.ROOT));
        employee.setDepartment(request.department().trim());
        employee.setPosition(request.position().trim());
        employee.setSalary(request.salary());
        employee.setHireDate(request.hireDate());
    }
}
