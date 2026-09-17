package com.expleo.simulator;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class CprBatchProcessor {

    private static final List<Map<String, String>> cprBuffer = new ArrayList<>();
    private static final AtomicInteger cprSeq = new AtomicInteger(1);

    private static final String BATCH_DIR = new File("").getAbsolutePath() + File.separator + "Files" + File.separator + "CPR_CNN_Batch";

    public static synchronized void addPacs008Transaction(Map<String, String> data, Logger logger) {
        if (data == null || data.isEmpty()) {
            return;
        }

        cprBuffer.add(new HashMap<>(data));

        int cprLimit = Config.getInt("cpr_batch_limit", 1000);

        if (logger != null) {
            logger.info("Added pacs.008 transaction to batch buffer. CPR count: " + cprBuffer.size() + "/" + cprLimit);
        }

        if (cprBuffer.size() >= cprLimit) {
            generateBatchFile("CPR", cprBuffer, cprSeq, cprLimit, logger);
        }
    }

    public static synchronized void flushRemaining(Logger logger) {
        int cprLimit = Config.getInt("cpr_batch_limit", 1000);

        if (!cprBuffer.isEmpty()) {
            if (logger != null) {
                logger.info("Flushing remaining " + cprBuffer.size() + " CPR transactions to batch file.");
            }
            generateBatchFile("CPR", cprBuffer, cprSeq, cprLimit, logger);
        }
    }

    private static void generateBatchFile(String reportType, List<Map<String, String>> buffer, AtomicInteger seqCounter, int batchLimit, Logger logger) {
        if (buffer.isEmpty()) {
            return;
        }

        int countToTake = Math.min(buffer.size(), batchLimit);
        List<Map<String, String>> batchItems = new ArrayList<>(buffer.subList(0, countToTake));
        buffer.subList(0, countToTake).clear();

        int seq = seqCounter.getAndIncrement();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(4));

        String dateStr = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String timeStr = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        String seqStr = String.format("%03d", seq);

        // SAMB_Pacs.002_CPR_20260902_164000_017.xml or SAMB_Pacs.002_CNN_20260902_164000_017.xml
        String fileName = "SAMB_Pacs.002_" + reportType + "_" + dateStr + "_" + timeStr + "_" + seqStr + ".xml";

        String xmlContent = ResponseCreator.buildBatchPacs002(batchItems, reportType, seq, now, logger);

        try {
            File dir = new File(BATCH_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File outputFile = new File(dir, fileName);
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(xmlContent);
            }

            if (logger != null) {
                logger.info("SUCCESS: Created " + reportType + " Batch File: " + outputFile.getAbsolutePath() + " with " + batchItems.size() + " transactions.");
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.severe("ERROR generating " + reportType + " Batch File: " + e.getMessage());
            }
        }
    }
}
