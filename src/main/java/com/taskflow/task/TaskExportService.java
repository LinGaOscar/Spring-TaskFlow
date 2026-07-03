package com.taskflow.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

// 匯出任務清單為 JSON/CSV/XLSX 三種格式，供看板頁匯出下拉選單使用
@Service
@RequiredArgsConstructor
public class TaskExportService {

    private final ObjectMapper objectMapper;

    private static final String[] HEADERS =
        { "標題", "狀態", "負責人", "到期日", "優先級", "描述" };

    public String toJson(List<TaskDto.Response> tasks) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tasks);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 開頭加 UTF-8 BOM，讓 Excel 直接開啟 CSV 時正確辨識繁中編碼
    public String toCsv(List<TaskDto.Response> tasks) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(String.join(",", HEADERS)).append('\n');
        for (TaskDto.Response t : tasks) {
            sb.append(csvField(t.getTitle())).append(',')
              .append(csvField(String.valueOf(t.getStatus()))).append(',')
              .append(csvField(t.getAssigneeName())).append(',')
              .append(csvField(t.getDueDate() != null ? t.getDueDate().toString() : "")).append(',')
              .append(csvField(String.valueOf(t.getPriority()))).append(',')
              .append(csvField(t.getDescription())).append('\n');
        }
        return sb.toString();
    }

    // RFC 4180：含逗號/引號/換行的欄位以雙引號包裹，內部引號翻倍
    private String csvField(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    public byte[] toXlsx(List<TaskDto.Response> tasks) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("任務清單");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int rowIdx = 1;
            for (TaskDto.Response t : tasks) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(t.getTitle());
                row.createCell(1).setCellValue(String.valueOf(t.getStatus()));
                row.createCell(2).setCellValue(t.getAssigneeName() != null ? t.getAssigneeName() : "");
                row.createCell(3).setCellValue(t.getDueDate() != null ? t.getDueDate().toString() : "");
                row.createCell(4).setCellValue(String.valueOf(t.getPriority()));
                row.createCell(5).setCellValue(t.getDescription() != null ? t.getDescription() : "");
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
