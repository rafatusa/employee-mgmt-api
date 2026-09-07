package com.udap.employee.web;

import com.udap.employee.dto.EmployeeRequest;
import com.udap.employee.dto.EmployeeResponse;
import com.udap.employee.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for employee records. */
@RestController
@RequestMapping("/api/v1/employees")
@Tag(name = "Employees", description = "Employee management operations")
@SecurityRequirement(name = "bearerAuth")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(final EmployeeService employeeService) {
        this.service = employeeService;
    }

    @GetMapping
    @Operation(summary = "List employees, optionally filtered by department")
    public Page<EmployeeResponse> list(
            @RequestParam(name = "department", required = false) final String department,
            @PageableDefault(size = 20) final Pageable pageable) {
        return service.list(department, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single employee by id")
    public EmployeeResponse get(@PathVariable("id") final Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Create a new employee")
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody final EmployeeRequest request) {
        final EmployeeResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/employees/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace an existing employee")
    public EmployeeResponse update(
            @PathVariable("id") final Long id, @Valid @RequestBody final EmployeeRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an employee")
    public ResponseEntity<Void> delete(@PathVariable("id") final Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
