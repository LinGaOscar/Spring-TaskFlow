package com.taskflow.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TaskExportServiceTest {

    @Autowired TaskExportService exportService;

    private TaskDto.Response sample() {
        TaskDto.Response r = new TaskDto.Response();
        r.setId(1L); r.setTitle("含,逗號\"引號"); r.setStatus(Task.Status.TODO);
        r.setPriority(Task.Priority.HIGH); r.setDueDate(LocalDate.of(2026, 7, 31));
        r.setAssigneeName("科長");
        return r;
    }

    @Test
    void csv_含BOM且特殊字元正確跳脫() {
        String csv = exportService.toCsv(List.of(sample()));
        assertThat(csv).startsWith("﻿");
        assertThat(csv).contains("\"含,逗號\"\"引號\"");
    }

    @Test
    void json_可序列化任務清單() {
        String json = exportService.toJson(List.of(sample()));
        assertThat(json).contains("\"title\"");
    }

    @Test
    void xlsx_產出可開啟的活頁簿() throws Exception {
        byte[] bytes = exportService.toXlsx(List.of(sample()));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue())
                .isEqualTo("含,逗號\"引號");
        }
    }
}
