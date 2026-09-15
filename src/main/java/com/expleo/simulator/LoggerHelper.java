package com.expleo.simulator;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.logging.*;

public class LoggerHelper {

    private static final String LOG_DIR = "Log";
    private static final String TEXT_LOG_FILE = LOG_DIR + "/stub_processor.log";
    private static final String EXCEL_AUDIT_FILE = LOG_DIR + "/Request_Audit.xlsx";
    private static final String TIMING_LOG_FILE = LOG_DIR + "/message_timing.txt";

    private static Logger rootLogger = null;

    public static synchronized Logger getLogger(String name) {
        if (rootLogger == null) {
            new File(LOG_DIR).mkdirs();
//            rootLogger = Logger.getLogger("StubLogger");
//            rootLogger.setUseParentHandlers(false); // disable console by default if we add our own
            rootLogger = Logger.getLogger(""); // Use the global root logger to capture all logs
            // rootLogger.setUseParentHandlers(false); // Do not disable parents for global root

            try {
                // Console Handler
                ConsoleHandler ch = new ConsoleHandler();
                ch.setLevel(Level.INFO);
                ch.setFormatter(new SimpleFormatter() {
                    private static final String format = "[%1$tF %1$tT] [%2$s] %3$s %n";
                    @Override
                    public synchronized String format(LogRecord lr) {
                        return String.format(format,
                                new java.util.Date(lr.getMillis()),
                                lr.getLevel().getLocalizedName(),
                                lr.getMessage()
                        );
                    }
                });
                rootLogger.addHandler(ch);

                // File Handler
                FileHandler fh = new FileHandler(TEXT_LOG_FILE, 5 * 1024 * 1024, 3, true);
                fh.setLevel(Level.INFO);
                fh.setFormatter(ch.getFormatter());
                rootLogger.addHandler(fh);

            } catch (Exception e) {
                System.err.println("Failed to setup log handlers: " + e.getMessage());
            }
        }
        return Logger.getLogger(name);
    }

    private static synchronized void ensureAuditExcelExists() {
        new File(LOG_DIR).mkdirs();
        File f = new File(EXCEL_AUDIT_FILE);
        if (f.exists()) {
            return;
        }

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet ws = wb.createSheet("Audit");
            Row row = ws.createRow(0);
            String[] headers = {
                    "ProcessedTime", "RequestFile", "BizMsgIdr", "MsgDefIdr", "CreDt",
                    "GrpHdr_MsgId", "InstrId", "EndToEndId", "UETR", "ResponseFile",
                    "Result", "Error"
            };
            for (int i = 0; i < headers.length; i++) {
                row.createCell(i).setCellValue(headers[i]);
            }
            try (FileOutputStream fos = new FileOutputStream(f)) {
                wb.write(fos);
            }
        } catch (Exception e) {
            getLogger("LoggerHelper").log(Level.SEVERE, "Could not create audit excel", e);
        }
    }

    public static synchronized void logRequestToExcel(Map<String, String> extracted, String requestFile, String responseFile, String result, String error) {
        ensureAuditExcelExists();
        File f = new File(EXCEL_AUDIT_FILE);
        Workbook wb = null;
        try {
            try (FileInputStream fis = new FileInputStream(f)) {
                wb = new XSSFWorkbook(fis);
            }
        } catch (Exception e) {
            // If corrupted
            if (e.getMessage() != null && (e.getMessage().contains("CRC") || e.getMessage().contains("Zip"))) {
                String backup = EXCEL_AUDIT_FILE + ".corrupted_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                try {
                    Files.move(f.toPath(), new File(backup).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    ensureAuditExcelExists();
                    try (FileInputStream fis = new FileInputStream(f)) {
                        wb = new XSSFWorkbook(fis);
                    }
                } catch (Exception ex) {
                    return;
                }
            } else {
                return;
            }
        }

        if (wb != null) {
            try {
                Sheet ws = wb.getSheet("Audit");
                if (ws == null) ws = wb.createSheet("Audit");

                int rowNum = ws.getLastRowNum() + 1;
                Row row = ws.createRow(rowNum);

                row.createCell(0).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                row.createCell(1).setCellValue(requestFile);
                row.createCell(2).setCellValue(extracted.getOrDefault("BizMsgIdr", ""));
                row.createCell(3).setCellValue(extracted.getOrDefault("MsgDefIdr", ""));
                row.createCell(4).setCellValue(extracted.getOrDefault("CreDt", ""));
                row.createCell(5).setCellValue(extracted.getOrDefault("GrpHdr_MsgId", ""));
                row.createCell(6).setCellValue(extracted.getOrDefault("InstrId", ""));
                row.createCell(7).setCellValue(extracted.getOrDefault("EndToEndId", ""));
                row.createCell(8).setCellValue(extracted.getOrDefault("UETR", ""));
                row.createCell(9).setCellValue(responseFile);
                row.createCell(10).setCellValue(result);
                row.createCell(11).setCellValue(error);

                try (FileOutputStream fos = new FileOutputStream(f)) {
                    wb.write(fos);
                }
                wb.close();
            } catch (Exception e) {
                // Ignore save errors
            }
        }
    }

    public static synchronized void logMessageTiming(LocalDateTime receiveTime, LocalDateTime sendTime, String inMsgId, String outMsgId) {
        new File(LOG_DIR).mkdirs();
        File f = new File(TIMING_LOG_FILE);
        try (java.io.FileWriter fw = new java.io.FileWriter(f, true);
             java.io.PrintWriter pw = new java.io.PrintWriter(fw)) {
            String formattedReceive = receiveTime != null ? receiveTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")) : "N/A";
            String formattedSend = sendTime != null ? sendTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")) : "N/A";
            long timeDiffMs = (receiveTime != null && sendTime != null) ? java.time.temporal.ChronoUnit.MILLIS.between(receiveTime, sendTime) : 0;
            pw.printf("Received: %s, Sent: %s, timeDiffMs: %d   InMsgId: %s, OutMsgId: %s%n",
                    formattedReceive, formattedSend, timeDiffMs, inMsgId, outMsgId);
        } catch (Exception e) {
            getLogger("LoggerHelper").log(Level.SEVERE, "Could not write to timing log", e);
        }
    }
}

