package com.hydroledger.backend.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hydroledger.backend.dto.CsvRowFailure;
import com.hydroledger.backend.dto.CsvUploadResultResponse;
import com.hydroledger.backend.dto.ResidentRequest;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.exception.ApiException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

/**
 * Bulk-registers residents from an admin-uploaded CSV or Excel (.xlsx/.xls) file.
 * Expected columns (header row required, case-insensitive, any order):
 * fullName, email, phone, houseNumber, meterNumber, governmentId, gender,
 * dateOfBirth, dateOfJoining, password (optional — present = set directly,
 * blank/missing = a set-password email is sent, same as the manual Add Resident flow),
 * flatSizeSqft (optional — household square footage, used by the common-area
 * floor-area distribution method).
 */
@Service
@RequiredArgsConstructor
public class ResidentBulkUploadService {

    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "fullname", "email", "phone", "housenumber", "meternumber",
            "governmentid", "gender", "dateofbirth", "dateofjoining");

    private final ResidentService residentService;
    private final Validator validator;

    // Deliberately NOT @Transactional: each row delegates to ResidentService.create(), which is
    // independently @Transactional. If this method were also transactional, one row's exception
    // would mark the single shared transaction rollback-only and silently discard the whole batch
    // (including already-successful rows) on commit. Leaving this method non-transactional means
    // each create() call gets its own transaction, so rows genuinely succeed/fail independently.
    public CsvUploadResultResponse bulkUpload(MultipartFile file, CommunityAdmin admin) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        List<ParsedRow> rows;

        if (filename.endsWith(".csv")) {
            rows = parseCsv(file);
        } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            rows = parseExcel(file);
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type — upload a .csv, .xlsx, or .xls file");
        }

        List<CsvRowFailure> failures = new ArrayList<>();
        int successCount = 0;

        for (ParsedRow row : rows) {
            try {
                ResidentRequest request = toRequest(row.fields());

                var violations = validator.validate(request);
                if (!violations.isEmpty()) {
                    String reason = violations.stream()
                            .map(ConstraintViolation::getMessage)
                            .reduce((a, b) -> a + "; " + b)
                            .orElse("Invalid row");
                    throw new IllegalArgumentException(reason);
                }

                residentService.create(request, admin);
                successCount++;
            } catch (ApiException ex) {
                failures.add(new CsvRowFailure(row.rowNumber(), row.rawLine(), ex.getMessage()));
            } catch (Exception ex) {
                failures.add(new CsvRowFailure(row.rowNumber(), row.rawLine(), ex.getMessage()));
            }
        }

        return new CsvUploadResultResponse(rows.size(), successCount, failures.size(), failures);
    }

    private ResidentRequest toRequest(Map<String, String> fields) {
        String password = fields.get("password");
        boolean bypassSetPassword = password != null && !password.isBlank();

        String flatSizeSqftRaw = fields.get("flatsizesqft");
        BigDecimal flatSizeSqft = null;
        if (flatSizeSqftRaw != null && !flatSizeSqftRaw.isBlank()) {
            try {
                flatSizeSqft = new BigDecimal(flatSizeSqftRaw.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("flatSizeSqft must be a number, got \"" + flatSizeSqftRaw + "\"");
            }
        }

        return new ResidentRequest(
                fields.get("fullname"),
                fields.get("email"),
                fields.get("phone"),
                fields.get("housenumber"),
                fields.get("meternumber"),
                fields.get("governmentid"),
                fields.get("gender"),
                fields.get("dateofbirth"),
                fields.get("dateofjoining"),
                bypassSetPassword,
                bypassSetPassword ? password : null,
                flatSizeSqft
        );
    }

    private List<ParsedRow> parseCsv(MultipartFile file) {
        List<ParsedRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
            }
            List<String> headers = normalizeHeaders(splitCsvLine(headerLine));
            requireHeaders(headers);

            String line;
            int rowNumber = 0;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) continue;
                List<String> cols = splitCsvLine(line);
                rows.add(new ParsedRow(rowNumber, line, toFieldMap(headers, cols)));
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }
        return rows;
    }

    private List<ParsedRow> parseExcel(MultipartFile file) {
        List<ParsedRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
            }

            List<String> headers = new ArrayList<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                headers.add(formatter.formatCellValue(headerRow.getCell(c)));
            }
            headers = normalizeHeaders(headers);
            requireHeaders(headers);

            int rowNumber = 0;
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row excelRow = sheet.getRow(r);
                if (excelRow == null) continue;
                rowNumber++;

                List<String> cols = new ArrayList<>();
                StringBuilder rawLine = new StringBuilder();
                for (int c = 0; c < headers.size(); c++) {
                    String value = formatter.formatCellValue(excelRow.getCell(c)).trim();
                    cols.add(value);
                    if (c > 0) rawLine.append(", ");
                    rawLine.append(value);
                }
                if (cols.stream().allMatch(String::isBlank)) continue;
                rows.add(new ParsedRow(rowNumber, rawLine.toString(), toFieldMap(headers, cols)));
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }
        return rows;
    }

    private List<String> normalizeHeaders(List<String> rawHeaders) {
        List<String> normalized = new ArrayList<>();
        for (String h : rawHeaders) {
            normalized.add(h == null ? "" : h.trim().toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private void requireHeaders(List<String> headers) {
        Set<String> present = Set.copyOf(headers);
        for (String required : REQUIRED_HEADERS) {
            if (!present.contains(required)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Missing required column: " + required
                                + ". Expected columns: fullName, email, phone, houseNumber, meterNumber, "
                                + "governmentId, gender, dateOfBirth, dateOfJoining, password (optional)");
            }
        }
    }

    private Map<String, String> toFieldMap(List<String> headers, List<String> cols) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String value = i < cols.size() ? cols.get(i).trim() : "";
            fields.put(headers.get(i), value.isBlank() ? null : value);
        }
        return fields;
    }

    private List<String> splitCsvLine(String line) {
        List<String> values = new ArrayList<>();
        for (String part : line.split(",", -1)) {
            values.add(part.trim());
        }
        return values;
    }

    private record ParsedRow(int rowNumber, String rawLine, Map<String, String> fields) {
    }
}
