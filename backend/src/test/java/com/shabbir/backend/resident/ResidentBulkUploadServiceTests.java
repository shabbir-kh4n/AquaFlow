package com.shabbir.backend.resident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import com.shabbir.backend.dto.CsvUploadResultResponse;
import com.shabbir.backend.dto.ResidentRequest;
import com.shabbir.backend.dto.ResidentResponse;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.exception.ApiException;
import com.shabbir.backend.service.ResidentBulkUploadService;
import com.shabbir.backend.service.ResidentService;

@ExtendWith(MockitoExtension.class)
class ResidentBulkUploadServiceTests {

    @Mock
    private ResidentService residentService;

    private Validator validator;
    private ResidentBulkUploadService bulkUploadService;

    private CommunityAdmin admin;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        bulkUploadService = new ResidentBulkUploadService(residentService, validator);
        admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "residents.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void allValidRowsAreCreated() {
        String csv = "fullName,email,phone,houseNumber,meterNumber,governmentId,gender,dateOfBirth,dateOfJoining,password\n"
                + "Ayan Test,ayan@example.com,9999999999,A-101,MTR-0001,991234,Male,2000-01-01,2026-01-01,\n"
                + "Priya Test,priya@example.com,8888888888,B-202,MTR-0002,995678,Female,2001-01-01,2026-01-01,mypassword1\n";

        when(residentService.create(any(ResidentRequest.class), eq(admin)))
                .thenReturn(new ResidentResponse(1L, "x", "x", "x", "x", "x", "x", "x", "x", "x", null, null));

        CsvUploadResultResponse result = bulkUploadService.bulkUpload(csvFile(csv), admin);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isZero();
        verify(residentService, times(2)).create(any(ResidentRequest.class), eq(admin));
    }

    @Test
    void rowWithPasswordSetsBypassFlagAndPassword() {
        String csv = "fullName,email,phone,houseNumber,meterNumber,governmentId,gender,dateOfBirth,dateOfJoining,password\n"
                + "Priya Test,priya@example.com,8888888888,B-202,MTR-0002,995678,Female,2001-01-01,2026-01-01,mypassword1\n";

        when(residentService.create(any(ResidentRequest.class), eq(admin)))
                .thenReturn(new ResidentResponse(1L, "x", "x", "x", "x", "x", "x", "x", "x", "x", null, null));

        bulkUploadService.bulkUpload(csvFile(csv), admin);

        ArgumentCaptor<ResidentRequest> captor = ArgumentCaptor.forClass(ResidentRequest.class);
        verify(residentService).create(captor.capture(), eq(admin));
        assertThat(captor.getValue().bypassSetPassword()).isTrue();
        assertThat(captor.getValue().password()).isEqualTo("mypassword1");
    }

    @Test
    void rowWithoutPasswordDoesNotBypass() {
        String csv = "fullName,email,phone,houseNumber,meterNumber,governmentId,gender,dateOfBirth,dateOfJoining,password\n"
                + "Ayan Test,ayan@example.com,9999999999,A-101,MTR-0001,991234,Male,2000-01-01,2026-01-01,\n";

        when(residentService.create(any(ResidentRequest.class), eq(admin)))
                .thenReturn(new ResidentResponse(1L, "x", "x", "x", "x", "x", "x", "x", "x", "x", null, null));

        bulkUploadService.bulkUpload(csvFile(csv), admin);

        ArgumentCaptor<ResidentRequest> captor = ArgumentCaptor.forClass(ResidentRequest.class);
        verify(residentService).create(captor.capture(), eq(admin));
        assertThat(captor.getValue().bypassSetPassword()).isFalse();
        assertThat(captor.getValue().password()).isNull();
    }

    @Test
    void rowMissingRequiredFieldIsReportedAsFailureNotException() {
        String csv = "fullName,email,phone,houseNumber,meterNumber,governmentId,gender,dateOfBirth,dateOfJoining,password\n"
                + ",ayan@example.com,9999999999,A-101,MTR-0001,991234,Male,2000-01-01,2026-01-01,\n";

        CsvUploadResultResponse result = bulkUploadService.bulkUpload(csvFile(csv), admin);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).contains("Full name is required");
    }

    @Test
    void rowWithInvalidMeterNumberFormatIsReportedAsFailure() {
        String csv = "fullName,email,phone,houseNumber,meterNumber,governmentId,gender,dateOfBirth,dateOfJoining,password\n"
                + "Ayan Test,ayan@example.com,9999999999,A-101,BADMETER,991234,Male,2000-01-01,2026-01-01,\n";

        CsvUploadResultResponse result = bulkUploadService.bulkUpload(csvFile(csv), admin);

        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).contains("Meter number");
    }

    @Test
    void duplicateResidentFromServiceIsReportedAsRowFailureNotThrown() {
        String csv = "fullName,email,phone,houseNumber,meterNumber,governmentId,gender,dateOfBirth,dateOfJoining,password\n"
                + "Ayan Test,ayan@example.com,9999999999,A-101,MTR-0001,991234,Male,2000-01-01,2026-01-01,\n";

        when(residentService.create(any(ResidentRequest.class), eq(admin)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "This house number is already assigned to another resident"));

        CsvUploadResultResponse result = bulkUploadService.bulkUpload(csvFile(csv), admin);

        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).contains("house number");
    }

    @Test
    void unsupportedFileExtensionThrows() {
        MockMultipartFile file = new MockMultipartFile("file", "residents.txt", "text/plain", "junk".getBytes());

        assertThatThrownBy(() -> bulkUploadService.bulkUpload(file, admin))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void missingRequiredColumnThrows() {
        String csv = "fullName,email,phone\nAyan,ayan@example.com,9999999999\n";

        assertThatThrownBy(() -> bulkUploadService.bulkUpload(csvFile(csv), admin))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Missing required column");
    }

    @Test
    void excelFileIsParsedCorrectly() throws IOException {
        byte[] excelBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Residents");
            Row header = sheet.createRow(0);
            String[] headers = {"fullName", "email", "phone", "houseNumber", "meterNumber",
                    "governmentId", "gender", "dateOfBirth", "dateOfJoining", "password"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row dataRow = sheet.createRow(1);
            String[] values = {"Ayan Test", "ayan@example.com", "9999999999", "A-101", "MTR-0001",
                    "991234", "Male", "2000-01-01", "2026-01-01", ""};
            for (int i = 0; i < values.length; i++) {
                dataRow.createCell(i).setCellValue(values[i]);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            excelBytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "residents.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        when(residentService.create(any(ResidentRequest.class), eq(admin)))
                .thenReturn(new ResidentResponse(1L, "x", "x", "x", "x", "x", "x", "x", "x", "x", null, null));

        CsvUploadResultResponse result = bulkUploadService.bulkUpload(file, admin);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);

        ArgumentCaptor<ResidentRequest> captor = ArgumentCaptor.forClass(ResidentRequest.class);
        verify(residentService).create(captor.capture(), eq(admin));
        assertThat(captor.getValue().fullName()).isEqualTo("Ayan Test");
        assertThat(captor.getValue().meterNumber()).isEqualTo("MTR-0001");
    }
}
