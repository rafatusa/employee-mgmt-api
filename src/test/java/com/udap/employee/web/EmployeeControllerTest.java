package com.udap.employee.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.udap.employee.config.SecurityConfig;
import com.udap.employee.dto.EmployeeRequest;
import com.udap.employee.dto.EmployeeResponse;
import com.udap.employee.service.DuplicateEmailException;
import com.udap.employee.service.EmployeeNotFoundException;
import com.udap.employee.service.EmployeeService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for the employee endpoints. The production security configuration is
 * excluded and the filter chain disabled, so the controller contract is asserted
 * independently of the JWT filter, which has its own dedicated unit test.
 */
@WebMvcTest(
        controllers = EmployeeController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeService service;

    private EmployeeResponse sample() {
        return new EmployeeResponse(
                1L, "Ada", "Lovelace", "ada@example.com", "Engineering", "Principal Engineer",
                new BigDecimal("185000.00"), LocalDate.of(2021, 3, 1),
                Instant.parse("2021-03-01T00:00:00Z"), Instant.parse("2021-03-01T00:00:00Z"));
    }

    private EmployeeRequest validRequest() {
        return new EmployeeRequest(
                "Ada", "Lovelace", "ada@example.com", "Engineering", "Principal Engineer",
                new BigDecimal("185000.00"), LocalDate.of(2021, 3, 1));
    }

    @Test
    void listReturnsPage() throws Exception {
        when(service.list(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample())));

        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("ada@example.com"));
    }

    @Test
    void listAcceptsDepartmentFilter() throws Exception {
        when(service.list(eq("Engineering"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample())));

        mockMvc.perform(get("/api/v1/employees").param("department", "Engineering"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].department").value("Engineering"));
    }

    @Test
    void getReturnsEmployee() throws Exception {
        when(service.get(1L)).thenReturn(sample());

        mockMvc.perform(get("/api/v1/employees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Ada"));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        when(service.get(99L)).thenThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(get("/api/v1/employees/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        when(service.create(any(EmployeeRequest.class))).thenReturn(sample());

        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createRejectsInvalidPayload() throws Exception {
        final EmployeeRequest invalid = new EmployeeRequest(
                "", "Lovelace", "not-an-email", "Engineering", "Engineer",
                new BigDecimal("-1"), LocalDate.of(2021, 3, 1));

        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details.firstName").exists());
    }

    @Test
    void createDuplicateReturns409() throws Exception {
        when(service.create(any(EmployeeRequest.class)))
                .thenThrow(new DuplicateEmailException("ada@example.com"));

        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void updateReturnsUpdatedEmployee() throws Exception {
        when(service.update(eq(1L), any(EmployeeRequest.class))).thenReturn(sample());

        mockMvc.perform(put("/api/v1/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Lovelace"));
    }

    @Test
    void deleteReturns204() throws Exception {
        doNothing().when(service).delete(1L);

        mockMvc.perform(delete("/api/v1/employees/1"))
                .andExpect(status().isNoContent());

        verify(service).delete(1L);
    }

    @Test
    void deleteUnknownReturns404() throws Exception {
        doThrow(new EmployeeNotFoundException(77L)).when(service).delete(77L);

        mockMvc.perform(delete("/api/v1/employees/77"))
                .andExpect(status().isNotFound());
    }
}
