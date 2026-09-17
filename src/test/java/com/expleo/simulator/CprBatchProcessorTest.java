package com.expleo.simulator;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class CprBatchProcessorTest {

    public static void main(String[] args) {
        Logger logger = LoggerHelper.getLogger("Test");
        logger.info("Starting CprBatchProcessorTest with custom CPR limit...");

        Config.set("cpr_batch_limit", 10);

        Map<String, String> sampleTx = new HashMap<>();
        sampleTx.put("msg_id", "TEST_MSG_001");
        sampleTx.put("end_to_end_id", "E2E_1234567890");
        sampleTx.put("tx_id", "TxId_9876543210");
        sampleTx.put("uetr", "df6e3757-cb9e-410b-9a0a-1af88247a413");
        sampleTx.put("clr_sys_ref", "CLR_REF_123456");
        sampleTx.put("amount", "100.00");
        sampleTx.put("currency", "AED");
        sampleTx.put("sttlm_dt", "2026-09-15");
        sampleTx.put("dbtr_bic", "DEUTAEAA");
        sampleTx.put("cdtr_bic", "SAMBAEAD");
        sampleTx.put("instg_bic", "SAMBAEAD");
        sampleTx.put("instd_bic", "AEPCAEA0");
        sampleTx.put("tx_sts", "ACCC");
        sampleTx.put("cdtr_nm", "Test Customer");
        sampleTx.put("cdtr_iban", "AE390260000825184296632");

        // Add 12 transactions (CPR limit=10)
        logger.info("Adding 12 transactions...");
        for (int i = 1; i <= 12; i++) {
            Map<String, String> tx = new HashMap<>(sampleTx);
            tx.put("end_to_end_id", "E2E_" + i);
            tx.put("tx_id", "TX_" + i);
            CprBatchProcessor.addPacs008Transaction(tx, logger);
        }

        // Flush remaining transactions
        CprBatchProcessor.flushRemaining(logger);

        logger.info("Test finished successfully.");
    }
}
