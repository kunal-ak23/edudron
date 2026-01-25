package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.dto.UserResponseDTO;
import com.datagami.edudron.student.domain.Class;
import com.datagami.edudron.student.domain.Enrollment;
import com.datagami.edudron.student.domain.Institute;
import com.datagami.edudron.student.domain.Section;
import com.datagami.edudron.student.dto.BulkStudentImportRequest;
import com.datagami.edudron.student.dto.BulkStudentImportResult;
import com.datagami.edudron.student.dto.CreateEnrollmentRequest;
import com.datagami.edudron.student.dto.StudentImportRowResult;
import com.datagami.edudron.student.dto.UpdateUserRequestDTO;
import com.datagami.edudron.student.repo.ClassRepository;
import com.datagami.edudron.student.repo.EnrollmentRepository;
import com.datagami.edudron.student.repo.InstituteRepository;
import com.datagami.edudron.student.repo.SectionRepository;
import com.datagami.edudron.student.util.PasswordGenerator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class BulkStudentImportService {
    
    private static final Logger log = LoggerFactory.getLogger(BulkStudentImportService.class);
    
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .setHeader()
            .build();
    
    @Autowired
    private EnrollmentService enrollmentService;
    
    @Autowired
    private ClassRepository classRepository;
    
    @Autowired
    private SectionRepository sectionRepository;
    
    @Autowired
    private InstituteRepository instituteRepository;
    
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    
    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;
    
    @Value("${IDENTITY_SERVICE_URL:http://localhost:8081}")
    private String identityServiceUrl;
    
    private RestTemplate restTemplate;
    
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            restTemplate = new RestTemplate();
            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
            
            // Add tenant context interceptor
            interceptors.add(new TenantContextRestTemplateInterceptor());
            
            // Add JWT forwarding interceptor - forwards Authorization header from incoming request
            interceptors.add((request, body, execution) -> {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest currentRequest = attributes.getRequest();
                    String authHeader = currentRequest.getHeader("Authorization");
                    if (authHeader != null && !authHeader.isBlank()) {
                        if (!request.getHeaders().containsKey("Authorization")) {
                            request.getHeaders().add("Authorization", authHeader);
                            log.debug("Forwarding Authorization header to identity service");
                        }
                    } else {
                        log.warn("No Authorization header found in incoming request - identity service calls may fail");
                    }
                }
                return execution.execute(request, body);
            });
            
            restTemplate.setInterceptors(interceptors);
        }
        return restTemplate;
    }
    
    public BulkStudentImportResult importStudents(MultipartFile file, BulkStudentImportRequest options) {
        BulkStudentImportResult result = new BulkStudentImportResult();
        List<StudentImportRowResult> rowResults = new ArrayList<>();
        
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            log.error("Tenant context is not set - cannot process import");
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        log.debug("Processing import for client ID: {}", clientId);
        
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            log.error("File name is required but not provided");
            throw new IllegalArgumentException("File name is required");
        }
        
        boolean isExcel = fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls");
        log.debug("File type detected: {}", isExcel ? "Excel" : "CSV");
        
        try {
            if (isExcel) {
                log.debug("Parsing Excel file...");
                importFromExcel(file, options, clientId, result, rowResults);
            } else {
                log.debug("Parsing CSV file...");
                importFromCSV(file, options, clientId, result, rowResults);
            }
            log.debug("File parsing completed. Total rows parsed: {}", rowResults.size());
        } catch (Exception e) {
            log.error("Error during bulk import", e);
            throw new RuntimeException("Failed to import students: " + e.getMessage(), e);
        }
        
        result.setRowResults(rowResults);
        result.setTotalRows((long) rowResults.size());
        result.setProcessedRows((long) rowResults.size());
        result.setSuccessfulRows(rowResults.stream().filter(StudentImportRowResult::getSuccess).count());
        result.setFailedRows(rowResults.stream().filter(r -> !r.getSuccess()).count());
        result.setSkippedRows(0L); // Could be enhanced to track skipped rows
        
        return result;
    }
    
    private void importFromCSV(MultipartFile file, BulkStudentImportRequest options, UUID clientId,
                              BulkStudentImportResult result, List<StudentImportRowResult> rowResults) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSV_FORMAT.parse(reader)) {
            
            for (CSVRecord record : parser) {
                if (isBlankRecord(record)) {
                    continue;
                }
                
                processStudentRow(record.getRecordNumber(), record, options, clientId, rowResults);
            }
        }
    }
    
    private void importFromExcel(MultipartFile file, BulkStudentImportRequest options, UUID clientId,
                                BulkStudentImportResult result, List<StudentImportRowResult> rowResults) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // Read header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel file must have a header row");
            }
            
            // Build column index map
            java.util.Map<String, Integer> columnMap = new java.util.HashMap<>();
            for (Cell cell : headerRow) {
                String header = getCellValueAsString(cell);
                if (StringUtils.hasText(header)) {
                    columnMap.put(header.trim().toLowerCase(), cell.getColumnIndex());
                }
            }
            
            // Process data rows
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                
                processExcelRow(i + 1, row, columnMap, options, clientId, rowResults);
            }
        }
    }
    
    private void processStudentRow(long rowNumber, CSVRecord record, BulkStudentImportRequest options,
                                  UUID clientId, List<StudentImportRowResult> rowResults) {
        String email = safeValue(record, "email");
        String name = safeValue(record, "name");
        String phone = safeValue(record, "phone");
        String password = safeValue(record, "password");
        
        // Try multiple case variations for instituteId
        String instituteId = safeValue(record, "instituteid");
        if (instituteId == null || instituteId.isEmpty()) {
            instituteId = safeValue(record, "instituteId");
        }
        if (instituteId == null || instituteId.isEmpty()) {
            instituteId = safeValue(record, "INSTITUTEID");
        }
        
        // Try multiple case variations for classId
        String classId = safeValue(record, "classid");
        if (classId == null || classId.isEmpty()) {
            classId = safeValue(record, "classId");
        }
        if (classId == null || classId.isEmpty()) {
            classId = safeValue(record, "CLASSID");
        }
        
        // Try multiple case variations for sectionId
        String sectionId = safeValue(record, "sectionid");
        if (sectionId == null || sectionId.isEmpty()) {
            sectionId = safeValue(record, "sectionId");
        }
        if (sectionId == null || sectionId.isEmpty()) {
            sectionId = safeValue(record, "SECTIONID");
        }
        
        // Try multiple case variations for courseId
        String courseId = safeValue(record, "courseid");
        if (courseId == null || courseId.isEmpty()) {
            courseId = safeValue(record, "courseId");
        }
        if (courseId == null || courseId.isEmpty()) {
            courseId = safeValue(record, "COURSEID");
        }
        
        processStudentData(rowNumber, email, name, phone, password, instituteId, classId, sectionId, courseId, options, clientId, rowResults);
    }
    
    private void processExcelRow(long rowNumber, Row row, java.util.Map<String, Integer> columnMap,
                                BulkStudentImportRequest options, UUID clientId, List<StudentImportRowResult> rowResults) {
        String email = getCellValue(row, columnMap, "email");
        String name = getCellValue(row, columnMap, "name");
        String phone = getCellValue(row, columnMap, "phone");
        String password = getCellValue(row, columnMap, "password");
        
        // Try multiple case variations for instituteId
        String instituteId = getCellValue(row, columnMap, "instituteid");
        if (instituteId == null || instituteId.isEmpty()) {
            instituteId = getCellValue(row, columnMap, "instituteId");
        }
        if (instituteId == null || instituteId.isEmpty()) {
            instituteId = getCellValue(row, columnMap, "INSTITUTEID");
        }
        
        // Try multiple case variations for classId
        String classId = getCellValue(row, columnMap, "classid");
        if (classId == null || classId.isEmpty()) {
            classId = getCellValue(row, columnMap, "classId");
        }
        if (classId == null || classId.isEmpty()) {
            classId = getCellValue(row, columnMap, "CLASSID");
        }
        
        // Try multiple case variations for sectionId
        String sectionId = getCellValue(row, columnMap, "sectionid");
        if (sectionId == null || sectionId.isEmpty()) {
            sectionId = getCellValue(row, columnMap, "sectionId");
        }
        if (sectionId == null || sectionId.isEmpty()) {
            sectionId = getCellValue(row, columnMap, "SECTIONID");
        }
        
        // Try multiple case variations for courseId
        String courseId = getCellValue(row, columnMap, "courseid");
        if (courseId == null || courseId.isEmpty()) {
            courseId = getCellValue(row, columnMap, "courseId");
        }
        if (courseId == null || courseId.isEmpty()) {
            courseId = getCellValue(row, columnMap, "COURSEID");
        }
        
        processStudentData(rowNumber, email, name, phone, password, instituteId, classId, sectionId, courseId, options, clientId, rowResults);
    }
    
    private void processStudentData(long rowNumber, String email, String name, String phone, String password,
                                   String instituteId, String classId, String sectionId, String courseId,
                                   BulkStudentImportRequest options, UUID clientId, List<StudentImportRowResult> rowResults) {
        StudentImportRowResult rowResult = new StudentImportRowResult();
        rowResult.setRowNumber(rowNumber);
        rowResult.setEmail(email);
        rowResult.setName(name);
        
        com.datagami.edudron.student.dto.CreateUserRequestDTO createUserRequest = null;
        try {
            // Validate required fields
            if (!StringUtils.hasText(email) || !StringUtils.hasText(name)) {
                rowResult.setSuccess(false);
                rowResult.setErrorMessage("Email and name are required");
                rowResults.add(rowResult);
                return;
            }
            
            // Generate password if needed
            if (!StringUtils.hasText(password) && (options.getAutoGeneratePassword() == null || options.getAutoGeneratePassword())) {
                password = PasswordGenerator.generatePassword();
            }
            
            if (!StringUtils.hasText(password)) {
                rowResult.setSuccess(false);
                rowResult.setErrorMessage("Password is required");
                rowResults.add(rowResult);
                return;
            }
            
            // Validate class/section/institute if provided
            if (StringUtils.hasText(sectionId)) {
                log.debug("Row {}: Looking up section: {} for client: {}", rowNumber, sectionId, clientId);
                Section section = sectionRepository.findByIdAndClientId(sectionId, clientId)
                    .orElse(null);
                if (section == null) {
                    log.warn("Row {}: Section not found: {} for client: {}", rowNumber, sectionId, clientId);
                    rowResult.setSuccess(false);
                    rowResult.setErrorMessage("Section not found: " + sectionId);
                    rowResults.add(rowResult);
                    return;
                }
                log.debug("Row {}: Section found: {} (name: {}, active: {}, classId: {})", 
                    rowNumber, section.getId(), section.getName(), section.getIsActive(), section.getClassId());
                    
                if (!section.getIsActive()) {
                    log.warn("Row {}: Section is not active: {}", rowNumber, sectionId);
                    rowResult.setSuccess(false);
                    rowResult.setErrorMessage("Section is not active: " + sectionId);
                    rowResults.add(rowResult);
                    return;
                }
                if (StringUtils.hasText(classId) && !section.getClassId().equals(classId)) {
                    log.warn("Row {}: Section {} belongs to class {} but classId {} was provided", 
                        rowNumber, sectionId, section.getClassId(), classId);
                    rowResult.setSuccess(false);
                    rowResult.setErrorMessage("Section does not belong to class: " + classId);
                    rowResults.add(rowResult);
                    return;
                }
                classId = section.getClassId();
                log.debug("Row {}: Using classId from section: {}", rowNumber, classId);
            }
            
            if (StringUtils.hasText(classId)) {
                log.debug("Row {}: Looking up class: {} for client: {}", rowNumber, classId, clientId);
                Class classEntity = classRepository.findByIdAndClientId(classId, clientId)
                    .orElse(null);
                if (classEntity == null) {
                    log.warn("Row {}: Class not found: {} for client: {}", rowNumber, classId, clientId);
                    rowResult.setSuccess(false);
                    rowResult.setErrorMessage("Class not found: " + classId);
                    rowResults.add(rowResult);
                    return;
                }
                log.debug("Row {}: Class found: {} (name: {}, active: {}, instituteId: {})", 
                    rowNumber, classEntity.getId(), classEntity.getName(), classEntity.getIsActive(), classEntity.getInstituteId());
                    
                if (!classEntity.getIsActive()) {
                    log.warn("Row {}: Class is not active: {}", rowNumber, classId);
                    rowResult.setSuccess(false);
                    rowResult.setErrorMessage("Class is not active: " + classId);
                    rowResults.add(rowResult);
                    return;
                }
                if (StringUtils.hasText(instituteId) && !classEntity.getInstituteId().equals(instituteId)) {
                    log.warn("Row {}: Class {} belongs to institute {} but instituteId {} was provided", 
                        rowNumber, classId, classEntity.getInstituteId(), instituteId);
                    rowResult.setSuccess(false);
                    rowResult.setErrorMessage("Class does not belong to institute: " + instituteId);
                    rowResults.add(rowResult);
                    return;
                }
                instituteId = classEntity.getInstituteId();
                log.debug("Row {}: Using instituteId from class: {}", rowNumber, instituteId);
            }
            
            if (StringUtils.hasText(instituteId)) {
                log.debug("Row {}: Looking up institute: {} for client: {}", rowNumber, instituteId, clientId);
                Institute institute = instituteRepository.findByIdAndClientId(instituteId, clientId)
                    .orElse(null);
                if (institute == null) {
                    log.warn("Row {}: Institute not found: {} for client: {}", rowNumber, instituteId, clientId);
                    rowResult.setSuccess(false);
                    rowResult.setErrorMessage("Institute not found: " + instituteId);
                    rowResults.add(rowResult);
                    return;
                }
                log.debug("Row {}: Institute found: {} (name: {}, active: {})", 
                    rowNumber, institute.getId(), institute.getName(), institute.getIsActive());
                    
                if (!institute.getIsActive()) {
                    log.warn("Row {}: Institute is not active: {}", rowNumber, instituteId);
                    rowResult.setSuccess(false);
                    rowResult.setErrorMessage("Institute is not active: " + instituteId);
                    rowResults.add(rowResult);
                    return;
                }
            } else {
                log.warn("Row {}: No instituteId found after processing", rowNumber);
            }
            
            // Create user via identity service
            createUserRequest = new com.datagami.edudron.student.dto.CreateUserRequestDTO();
            createUserRequest.setEmail(email);
            createUserRequest.setPassword(password);
            createUserRequest.setName(name);
            createUserRequest.setPhone(phone);
            createUserRequest.setRole("STUDENT");
            createUserRequest.setActive(true);
            
            // Set instituteIds - required for non-SYSTEM_ADMIN users
            if (StringUtils.hasText(instituteId)) {
                createUserRequest.setInstituteIds(java.util.Collections.singletonList(instituteId));
            } else {
                log.warn("Row {}: Institute ID is required but not provided for student: {}", rowNumber, email);
                rowResult.setSuccess(false);
                rowResult.setErrorMessage("Institute ID is required");
                rowResults.add(rowResult);
                return;
            }
            
            // Set autoGeneratePassword based on options (password is already generated if needed)
            if (options.getAutoGeneratePassword() != null && options.getAutoGeneratePassword()) {
                createUserRequest.setAutoGeneratePassword(true);
            }
            
            log.debug("Row {}: Creating user in identity service: {} (institute: {}, class: {}, section: {})", 
                rowNumber, email, instituteId, classId, sectionId);
            
            String identityUrl = gatewayUrl + "/idp/users";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<com.datagami.edudron.student.dto.CreateUserRequestDTO> entity = new HttpEntity<>(createUserRequest, headers);
            
            ResponseEntity<UserResponseDTO> response = getRestTemplate().exchange(
                identityUrl,
                HttpMethod.POST,
                entity,
                UserResponseDTO.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                UserResponseDTO user = response.getBody();
                log.info("Row {}: Successfully created user: {} (ID: {})", rowNumber, email, user.getId());
                rowResult.setSuccess(true);
                rowResult.setStudentId(user.getId());
                
                // Handle enrollments and associations
                handleEnrollmentsAndAssociations(user, classId, sectionId, courseId, instituteId, options, clientId, rowResult);
            } else {
                log.error("Row {}: Failed to create user: {} - Status: {}", rowNumber, email, response.getStatusCode());
                rowResult.setSuccess(false);
                rowResult.setErrorMessage("Failed to create user: " + (response.getBody() != null ? response.getBody().toString() : "Unknown error"));
            }
            
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String errorMessage = "Failed to create user";
            try {
                if (e.getResponseBodyAsString() != null) {
                    errorMessage = e.getResponseBodyAsString();
                }
            } catch (Exception ignored) {}
            
            // Check if user already exists
            if (errorMessage.contains("already exists") || errorMessage.contains("User already exists")) {
                if (options.getUpsertExisting() != null && options.getUpsertExisting()) {
                    // Try to update existing user
                    try {
                        UserResponseDTO existingUser = findUserByEmail(email, clientId);
                        if (existingUser != null) {
                            // Update the user
                            UserResponseDTO updatedUser = updateExistingUser(existingUser.getId(), email, name, phone, instituteId, clientId);
                            rowResult.setSuccess(true);
                            rowResult.setStudentId(updatedUser.getId());
                            
                            // Handle enrollments and associations (same as for new users)
                            handleEnrollmentsAndAssociations(updatedUser, classId, sectionId, courseId, instituteId, options, clientId, rowResult);
                        } else {
                            rowResult.setSuccess(false);
                            rowResult.setErrorMessage("User already exists but could not be found for update");
                        }
                    } catch (Exception updateException) {
                        log.error("Row {}: Failed to update existing user {}: {}", rowNumber, email, updateException.getMessage());
                        rowResult.setSuccess(false);
                        rowResult.setErrorMessage("Failed to update existing user: " + updateException.getMessage());
                    }
                } else {
                    rowResult.setSuccess(false);
                    rowResult.setErrorMessage("User already exists with this email");
                }
            } else {
                rowResult.setSuccess(false);
                rowResult.setErrorMessage(errorMessage);
            }
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            String errorMessage = "Internal server error";
            try {
                if (e.getResponseBodyAsString() != null) {
                    errorMessage = e.getResponseBodyAsString();
                }
            } catch (Exception ignored) {}
            
            // Check if this is a "user already exists" error (sometimes returned as 500)
            if (errorMessage.contains("already exists") || errorMessage.contains("User already exists")) {
                if (options.getUpsertExisting() != null && options.getUpsertExisting()) {
                    // Try to update existing user
                    try {
                        UserResponseDTO existingUser = findUserByEmail(email, clientId);
                        if (existingUser != null) {
                            // Update the user
                            UserResponseDTO updatedUser = updateExistingUser(existingUser.getId(), email, name, phone, instituteId, clientId);
                            rowResult.setSuccess(true);
                            rowResult.setStudentId(updatedUser.getId());
                            
                            // Handle enrollments and associations (same as for new users)
                            handleEnrollmentsAndAssociations(updatedUser, classId, sectionId, courseId, instituteId, options, clientId, rowResult);
                        } else {
                            rowResult.setSuccess(false);
                            rowResult.setErrorMessage("User already exists but could not be found for update");
                        }
                    } catch (Exception updateException) {
                        log.error("Row {}: Failed to update existing user {}: {}", rowNumber, email, updateException.getMessage());
                        rowResult.setSuccess(false);
                        rowResult.setErrorMessage("Failed to update existing user: " + updateException.getMessage());
                    }
                } else {
                    log.error("Row {}: Server error creating user. Status: {}, Response: {}", 
                        rowNumber, e.getStatusCode(), errorMessage);
                    rowResult.setSuccess(false);
                    rowResult.setErrorMessage(errorMessage);
                }
            } else {
                log.error("Row {}: Server error creating user. Status: {}, Response: {}", 
                    rowNumber, e.getStatusCode(), errorMessage);
                rowResult.setSuccess(false);
                rowResult.setErrorMessage(errorMessage);
            }
        } catch (Exception e) {
            log.error("Row {}: Unexpected error creating user", rowNumber, e);
            rowResult.setSuccess(false);
            rowResult.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Unknown error: " + e.getClass().getSimpleName());
        }
        
        rowResults.add(rowResult);
    }
    
    private boolean isBlankRecord(CSVRecord record) {
        for (String value : record) {
            if (StringUtils.hasText(value)) {
                return false;
            }
        }
        return true;
    }
    
    private String safeValue(CSVRecord record, String key) {
        try {
            String value = record.get(key);
            String trimmed = value != null ? value.trim() : null;
            if (trimmed != null && trimmed.isEmpty()) {
                trimmed = null;
            }
            return trimmed;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    private String getCellValue(Row row, java.util.Map<String, Integer> columnMap, String columnName) {
        Integer index = columnMap.get(columnName.toLowerCase());
        if (index == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        String value = getCellValueAsString(cell);
        if (value != null && value.isEmpty()) {
            value = null;
        }
        return value;
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Remove decimal if it's a whole number
                    double numValue = cell.getNumericCellValue();
                    if (numValue == (long) numValue) {
                        return String.valueOf((long) numValue);
                    } else {
                        return String.valueOf(numValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
    
    /**
     * Find user by email by searching through all users
     */
    private UserResponseDTO findUserByEmail(String email, UUID clientId) {
        try {
            String usersUrl = gatewayUrl + "/idp/users";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<UserResponseDTO[]> response = getRestTemplate().exchange(
                usersUrl,
                HttpMethod.GET,
                entity,
                UserResponseDTO[].class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                for (UserResponseDTO user : response.getBody()) {
                    if (email.equalsIgnoreCase(user.getEmail()) && 
                        (clientId == null || clientId.equals(user.getClientId()))) {
                        return user;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to find user by email {}: {}", email, e.getMessage());
        }
        return null;
    }
    
    /**
     * Update an existing user
     */
    private UserResponseDTO updateExistingUser(String userId, String email, String name, String phone, 
                                               String instituteId, UUID clientId) {
        try {
            // Create update request
            UpdateUserRequestDTO updateRequest = new UpdateUserRequestDTO();
            updateRequest.setEmail(email);
            updateRequest.setName(name);
            updateRequest.setPhone(phone);
            updateRequest.setRole("STUDENT");
            updateRequest.setActive(true);
            if (StringUtils.hasText(instituteId)) {
                updateRequest.setInstituteIds(java.util.Collections.singletonList(instituteId));
            }
            
            String updateUrl = gatewayUrl + "/idp/users/" + userId;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<UpdateUserRequestDTO> entity = new HttpEntity<>(updateRequest, headers);
            
            ResponseEntity<UserResponseDTO> response = getRestTemplate().exchange(
                updateUrl,
                HttpMethod.PUT,
                entity,
                UserResponseDTO.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to update user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to update user: " + e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * Handle enrollments and class/section associations for a user
     */
    private void handleEnrollmentsAndAssociations(UserResponseDTO user, String classId, String sectionId, 
                                                   String courseId, String instituteId,
                                                   BulkStudentImportRequest options, UUID clientId,
                                                   StudentImportRowResult rowResult) {
        log.debug("Handling enrollments and associations for student: {} (class: {}, section: {}, course: {})", 
            user.getId(), classId, sectionId, courseId);
        boolean hasEnrollment = false;
        
        // Create enrollment if needed
        if (options.getAutoEnroll() != null && options.getAutoEnroll() && StringUtils.hasText(courseId)) {
            try {
                log.debug("Auto-enrolling student {} in course: {}", user.getId(), courseId);
                CreateEnrollmentRequest enrollmentRequest = new CreateEnrollmentRequest();
                enrollmentRequest.setCourseId(courseId);
                enrollmentRequest.setClassId(classId);
                enrollmentRequest.setBatchId(sectionId); // batchId is used for sectionId
                enrollmentRequest.setInstituteId(instituteId);
                
                enrollmentService.enrollStudent(user.getId(), enrollmentRequest);
                hasEnrollment = true;
                log.debug("Successfully enrolled student {} in course: {}", user.getId(), courseId);
            } catch (Exception e) {
                log.warn("Failed to enroll student {} in course {}: {}", user.getId(), courseId, e.getMessage());
                // Don't fail the import if enrollment fails
            }
        }
        
        // Enroll in default courses if provided
        if (options.getDefaultCourseIds() != null && !options.getDefaultCourseIds().isEmpty()) {
            for (String defaultCourseId : options.getDefaultCourseIds()) {
                try {
                    CreateEnrollmentRequest enrollmentRequest = new CreateEnrollmentRequest();
                    enrollmentRequest.setCourseId(defaultCourseId);
                    enrollmentRequest.setClassId(classId);
                    enrollmentRequest.setBatchId(sectionId);
                    enrollmentRequest.setInstituteId(instituteId);
                    
                    enrollmentService.enrollStudent(user.getId(), enrollmentRequest);
                    hasEnrollment = true;
                } catch (Exception e) {
                    log.warn("Failed to enroll student {} in default course {}: {}", user.getId(), defaultCourseId, e.getMessage());
                }
            }
        }
        
        // If student has classId or sectionId but no enrollment created yet, create a placeholder enrollment
        if (!hasEnrollment && (StringUtils.hasText(classId) || StringUtils.hasText(sectionId))) {
            log.debug("Creating placeholder enrollment for student {} (class: {}, section: {})", 
                user.getId(), classId, sectionId);
            try {
                String placeholderCourseId = "__PLACEHOLDER_ASSOCIATION__";
                
                // Check if placeholder enrollment already exists
                // Use try-catch to handle potential transaction errors
                boolean exists = false;
                try {
                    exists = enrollmentRepository.existsByClientIdAndStudentIdAndCourseId(clientId, user.getId(), placeholderCourseId);
                    log.debug("Placeholder enrollment exists for student {}: {}", user.getId(), exists);
                } catch (Exception checkException) {
                    // If the check fails (e.g., transaction aborted), log and skip creation
                    log.debug("Could not check for existing placeholder enrollment for student {}: {}", 
                        user.getId(), checkException.getMessage());
                    return; // Skip enrollment creation if we can't check
                }
                
                if (!exists) {
                    // Create enrollment directly in repository to associate student with class/section
                    Enrollment placeholderEnrollment = new Enrollment();
                    placeholderEnrollment.setId(com.datagami.edudron.common.UlidGenerator.nextUlid());
                    placeholderEnrollment.setClientId(clientId);
                    placeholderEnrollment.setStudentId(user.getId());
                    placeholderEnrollment.setCourseId(placeholderCourseId);
                    placeholderEnrollment.setClassId(classId);
                    placeholderEnrollment.setBatchId(sectionId); // batchId is used for sectionId
                    placeholderEnrollment.setInstituteId(instituteId);
                    
                    enrollmentRepository.save(placeholderEnrollment);
                    log.info("Created placeholder enrollment for student {} to associate with class {} / section {}", 
                        user.getId(), classId, sectionId);
                    
                    // Automatically enroll student in all published courses assigned to this section/class
                    try {
                        log.debug("Auto-enrolling student {} in courses assigned to class/section", user.getId());
                        enrollmentService.autoEnrollStudentInAssignedCourses(
                            user.getId(), sectionId, classId, instituteId, clientId);
                    } catch (Exception e) {
                        // Log error but don't fail the import if auto-enrollment fails
                        log.warn("Failed to auto-enroll student {} in assigned courses after placeholder enrollment: {}", 
                            user.getId(), e.getMessage(), e);
                    }
                } else {
                    log.debug("Placeholder enrollment already exists for student {}, skipping creation", user.getId());
                }
            } catch (org.springframework.dao.DataAccessException e) {
                // Handle database-specific errors (including transaction aborted)
                log.warn("Failed to create placeholder enrollment for student {} due to database error: {}", 
                    user.getId(), e.getMessage());
                // Don't fail the import if placeholder enrollment fails
            } catch (Exception e) {
                log.warn("Failed to create placeholder enrollment for student {}: {}", user.getId(), e.getMessage());
                // Don't fail the import if placeholder enrollment fails
            }
        } else {
            log.debug("No placeholder enrollment needed for student {} (hasEnrollment: {}, classId: {}, sectionId: {})", 
                user.getId(), hasEnrollment, classId, sectionId);
        }
    }
}

