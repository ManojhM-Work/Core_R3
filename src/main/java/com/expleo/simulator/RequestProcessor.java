package com.expleo.simulator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class RequestProcessor {

    private static final String BASE_DIR = new File("").getAbsolutePath();

    private static String getSafeXPathText(Document doc, XPath xpath, String expression) {
        try {
            String result = (String) xpath.evaluate(expression, doc, XPathConstants.STRING);
            return result != null ? result.trim() : "";
        } catch (XPathExpressionException e) {
            return "";
        }
    }

    public static Map<String, String> parsePacs008(String xmlText, Logger logger, boolean isPerf) {
        Map<String, String> data = new HashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Important: namespace aware but we will query using local-name() to circumvent
            // strict namespace matching
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlText.getBytes(StandardCharsets.UTF_8)));

            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();

            data.put("msg_id", getSafeXPathText(doc, xpath, "//*[local-name()='GrpHdr']/*[local-name()='MsgId']"));
            data.put("cre_dt_tm", getSafeXPathText(doc, xpath, "//*[local-name()='GrpHdr']/*[local-name()='CreDtTm']"));
            data.put("nb_of_txs", getSafeXPathText(doc, xpath, "//*[local-name()='GrpHdr']/*[local-name()='NbOfTxs']"));

            data.put("instr_id", getSafeXPathText(doc, xpath, "//*[local-name()='PmtId']/*[local-name()='InstrId']"));
            data.put("end_to_end_id",
                    getSafeXPathText(doc, xpath, "//*[local-name()='PmtId']/*[local-name()='EndToEndId']"));
            data.put("tx_id", getSafeXPathText(doc, xpath, "//*[local-name()='PmtId']/*[local-name()='TxId']"));
            data.put("uetr", getSafeXPathText(doc, xpath, "//*[local-name()='PmtId']/*[local-name()='UETR']"));
            data.put("clr_sys_ref",
                    getSafeXPathText(doc, xpath, "//*[local-name()='PmtId']/*[local-name()='ClrSysRef']"));
            data.put("sts_id", getSafeXPathText(doc, xpath, "//*[local-name()='PmtId']/*[local-name()='StsId']"));

            data.put("lcl_instrm_prtry", getSafeXPathText(doc, xpath,
                    "//*[local-name()='PmtTpInf']/*[local-name()='LclInstrm']/*[local-name()='Prtry']"));
            data.put("lcl_instrm_cd", getSafeXPathText(doc, xpath,
                    "//*[local-name()='PmtTpInf']/*[local-name()='LclInstrm']/*[local-name()='Cd']"));
            data.put("svc_lvl_prtry", getSafeXPathText(doc, xpath,
                    "//*[local-name()='PmtTpInf']/*[local-name()='SvcLvl']/*[local-name()='Prtry']"));
            data.put("svc_lvl_cd", getSafeXPathText(doc, xpath,
                    "//*[local-name()='PmtTpInf']/*[local-name()='SvcLvl']/*[local-name()='Cd']"));
            data.put("ctgy_purp_prtry", getSafeXPathText(doc, xpath,
                    "//*[local-name()='PmtTpInf']/*[local-name()='CtgyPurp']/*[local-name()='Prtry']"));
            data.put("ctgy_purp_cd", getSafeXPathText(doc, xpath,
                    "//*[local-name()='PmtTpInf']/*[local-name()='CtgyPurp']/*[local-name()='Cd']"));

            data.put("amount", getSafeXPathText(doc, xpath, "//*[local-name()='IntrBkSttlmAmt']"));
            // Currency is an attribute
            String ccy = "";
            try {
                Element amtNode = (Element) xpath.evaluate("//*[local-name()='IntrBkSttlmAmt']", doc,
                        XPathConstants.NODE);
                if (amtNode != null) {
                    ccy = amtNode.getAttribute("Ccy");
                }
            } catch (Exception ignored) {
            }
            data.put("currency", ccy);

            data.put("sttlm_dt",
                    getSafeXPathText(doc, xpath, "//*[local-name()='GrpHdr']/*[local-name()='IntrBkSttlmDt']"));

            data.put("dbtr_bic", getSafeXPathText(doc, xpath,
                    "//*[local-name()='DbtrAgt']/*[local-name()='FinInstnId']/*[local-name()='BICFI']"));
            data.put("cdtr_bic", getSafeXPathText(doc, xpath,
                    "//*[local-name()='CdtrAgt']/*[local-name()='FinInstnId']/*[local-name()='BICFI']"));

            data.put("instg_bic", getSafeXPathText(doc, xpath,
                    "//*[local-name()='GrpHdr']/*[local-name()='InstgAgt']/*[local-name()='FinInstnId']/*[local-name()='BICFI']"));
            data.put("instd_bic", getSafeXPathText(doc, xpath,
                    "//*[local-name()='GrpHdr']/*[local-name()='InstdAgt']/*[local-name()='FinInstnId']/*[local-name()='BICFI']"));

            data.put("cdtr_nm", getSafeXPathText(doc, xpath, "//*[local-name()='Cdtr']//*[local-name()='Nm']"));
            data.put("cdtr_iban", getSafeXPathText(doc, xpath, "//*[local-name()='CdtrAcct']/*[local-name()='Id']/*[local-name()='IBAN']"));
            data.put("cdtr_acct_tp", getSafeXPathText(doc, xpath, "//*[local-name()='CdtrAcct']/*[local-name()='Tp']/*[local-name()='Cd']"));
            data.put("cdtr_org_id", getSafeXPathText(doc, xpath, "//*[local-name()='Cdtr']//*[local-name()='OrgId']//*[local-name()='Othr']/*[local-name()='Id']"));
            data.put("cdtr_org_schme", getSafeXPathText(doc, xpath, "//*[local-name()='Cdtr']//*[local-name()='OrgId']//*[local-name()='Othr']/*[local-name()='SchmeNm']/*[local-name()='Cd']"));
            data.put("cdtr_org_issr", getSafeXPathText(doc, xpath, "//*[local-name()='Cdtr']//*[local-name()='OrgId']//*[local-name()='Othr']/*[local-name()='Issr']"));
            data.put("cdtr_prvt_birth_dt", getSafeXPathText(doc, xpath, "//*[local-name()='Cdtr']//*[local-name()='PrvtId']//*[local-name()='BirthDt']"));
            data.put("cdtr_prvt_city", getSafeXPathText(doc, xpath, "//*[local-name()='Cdtr']//*[local-name()='PrvtId']//*[local-name()='CityOfBirth']"));
            data.put("cdtr_prvt_ctry", getSafeXPathText(doc, xpath, "//*[local-name()='Cdtr']//*[local-name()='PrvtId']//*[local-name()='CtryOfBirth']"));
            data.put("cdtr_prvt_id", getSafeXPathText(doc, xpath, "//*[local-name()='Cdtr']//*[local-name()='PrvtId']//*[local-name()='Othr']/*[local-name()='Id']"));
            data.put("cdtr_prvt_schme", getSafeXPathText(doc, xpath, "//*[local-name()='Cdtr']//*[local-name()='PrvtId']//*[local-name()='Othr']/*[local-name()='SchmeNm']/*[local-name()='Cd']"));
            data.put("cdtr_prvt_issr", getSafeXPathText(doc, xpath, "//*[local-name()='Cdtr']//*[local-name()='PrvtId']//*[local-name()='Othr']/*[local-name()='Issr']"));


            if (!isPerf) {
                logger.info("=== Safely Extracted from PACS.008 ===");
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    logger.info(entry.getKey() + " = " + entry.getValue());
                }
            }
        } catch (Exception e) {
            logger.severe("Failed to parse pacs008: " + e.getMessage());
        }
        return data;
    }

    public static Map<String, String> parsePacs007(String xmlText, Logger logger, boolean isPerf) {
        Map<String, String> data = new HashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlText.getBytes(StandardCharsets.UTF_8)));

            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();

            data.put("msg_id", getSafeXPathText(doc, xpath, "//*[local-name()='GrpHdr']/*[local-name()='MsgId']"));
            data.put("cre_dt_tm", getSafeXPathText(doc, xpath, "//*[local-name()='GrpHdr']/*[local-name()='CreDtTm']"));
            data.put("nb_of_txs", getSafeXPathText(doc, xpath, "//*[local-name()='GrpHdr']/*[local-name()='NbOfTxs']"));

            data.put("rvsl_id", getSafeXPathText(doc, xpath, "//*[local-name()='TxInf']/*[local-name()='RvslId']"));
            data.put("orgnl_msg_id", getSafeXPathText(doc, xpath,
                    "//*[local-name()='TxInf']/*[local-name()='OrgnlGrpInf']/*[local-name()='OrgnlMsgId']"));
            data.put("orgnl_msg_nm_id", getSafeXPathText(doc, xpath,
                    "//*[local-name()='TxInf']/*[local-name()='OrgnlGrpInf']/*[local-name()='OrgnlMsgNmId']"));
            data.put("orgnl_cre_dt_tm", getSafeXPathText(doc, xpath,
                    "//*[local-name()='TxInf']/*[local-name()='OrgnlGrpInf']/*[local-name()='OrgnlCreDtTm']"));
            data.put("orgnl_end_to_end_id",
                    getSafeXPathText(doc, xpath, "//*[local-name()='TxInf']/*[local-name()='OrgnlEndToEndId']"));
            data.put("orgnl_tx_id",
                    getSafeXPathText(doc, xpath, "//*[local-name()='TxInf']/*[local-name()='OrgnlTxId']"));
            data.put("orgnl_uetr",
                    getSafeXPathText(doc, xpath, "//*[local-name()='TxInf']/*[local-name()='OrgnlUETR']"));
            data.put("orgnl_clr_sys_ref",
                    getSafeXPathText(doc, xpath, "//*[local-name()='TxInf']/*[local-name()='OrgnlClrSysRef']"));

            String amount = getSafeXPathText(doc, xpath,
                    "//*[local-name()='TxInf']/*[local-name()='OrgnlIntrBkSttlmAmt']");
            String ccy = "";
            try {
                Element amtNode = (Element) xpath.evaluate(
                        "//*[local-name()='TxInf']/*[local-name()='OrgnlIntrBkSttlmAmt']", doc, XPathConstants.NODE);
                if (amtNode == null) {
                    amtNode = (Element) xpath.evaluate("//*[local-name()='TxInf']/*[local-name()='RvsdIntrBkSttlmAmt']",
                            doc, XPathConstants.NODE);
                    amount = getSafeXPathText(doc, xpath,
                            "//*[local-name()='TxInf']/*[local-name()='RvsdIntrBkSttlmAmt']");
                }
                if (amtNode != null) {
                    ccy = amtNode.getAttribute("Ccy");
                }
            } catch (Exception ignored) {
            }
            data.put("amount", amount);
            data.put("currency", ccy);

            String sttlmDt = getSafeXPathText(doc, xpath, "//*[local-name()='TxInf']/*[local-name()='IntrBkSttlmDt']");
            if (sttlmDt == null || sttlmDt.isEmpty()) {
                sttlmDt = getSafeXPathText(doc, xpath, "//*[local-name()='GrpHdr']/*[local-name()='IntrBkSttlmDt']");
            }
            data.put("sttlm_dt", sttlmDt);

            data.put("rsn_cd", getSafeXPathText(doc, xpath,
                    "//*[local-name()='TxInf']/*[local-name()='RvslRsnInf']/*[local-name()='Rsn']/*[local-name()='Cd']"));

            data.put("instg_bic", getSafeXPathText(doc, xpath,
                    "//*[local-name()='GrpHdr']/*[local-name()='InstgAgt']/*[local-name()='FinInstnId']/*[local-name()='BICFI']"));
            data.put("instd_bic", getSafeXPathText(doc, xpath,
                    "//*[local-name()='GrpHdr']/*[local-name()='InstdAgt']/*[local-name()='FinInstnId']/*[local-name()='BICFI']"));

            data.put("mndt_id", getSafeXPathText(doc, xpath,
                    "//*[local-name()='TxInf']/*[local-name()='OrgnlTxRef']/*[local-name()='MndtRltdInf']/*[local-name()='DrctDbtMndt']/*[local-name()='MndtId']"));

            // System.out.println(data.get("instg_bic")+
            // "---------------------------------");

            // Mapping to support buildPacs002 and buildPacs004
            data.put("dbtr_bic", data.get("instg_bic"));
            data.put("cdtr_bic", data.get("instd_bic"));
            data.put("end_to_end_id", data.get("orgnl_end_to_end_id"));
            data.put("tx_id", data.get("orgnl_tx_id"));
            data.put("uetr", data.get("orgnl_uetr"));
            data.put("clr_sys_ref", data.get("orgnl_clr_sys_ref"));
            data.put("msg_type", "pacs.007");

            if (!isPerf) {
                logger.info("=== Safely Extracted from PACS.007 ===");
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    logger.info(entry.getKey() + " = " + entry.getValue());
                }
            }
        } catch (Exception e) {
            logger.severe("Failed to parse pacs007: " + e.getMessage());
        }
        return data;
    }

    // public static Map<String, String> parsePacs004(String xmlText, Logger logger,
    // boolean isPerf) {
    // Map<String, String> data = new HashMap<>();
    // try {
    // DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    // factory.setNamespaceAware(true);
    // DocumentBuilder builder = factory.newDocumentBuilder();
    // Document doc = builder.parse(new
    // ByteArrayInputStream(xmlText.getBytes(StandardCharsets.UTF_8)));
    //
    // XPathFactory xPathfactory = XPathFactory.newInstance();
    // XPath xpath = xPathfactory.newXPath();
    //
    // data.put("msg_id", getSafeXPathText(doc, xpath,
    // "//*[local-name()='GrpHdr']/*[local-name()='MsgId']"));
    // data.put("cre_dt_tm", getSafeXPathText(doc, xpath,
    // "//*[local-name()='GrpHdr']/*[local-name()='CreDtTm']"));
    // data.put("nb_of_txs", getSafeXPathText(doc, xpath,
    // "//*[local-name()='GrpHdr']/*[local-name()='NbOfTxs']"));
    //
    // data.put("end_to_end_id", getSafeXPathText(doc, xpath,
    // "//*[local-name()='TxInf']/*[local-name()='OrgnlEndToEndId']"));
    // data.put("tx_id", getSafeXPathText(doc, xpath,
    // "//*[local-name()='TxInf']/*[local-name()='OrgnlTxId']"));
    // data.put("uetr", getSafeXPathText(doc, xpath,
    // "//*[local-name()='TxInf']/*[local-name()='OrgnlUETR']"));
    // data.put("orgnl_clr_sys_ref", getSafeXPathText(doc, xpath,
    // "//*[local-name()='TxInf']/*[local-name()='OrgnlClrSysRef']"));
    //
    // String orgnlMsgNmId = getSafeXPathText(doc, xpath,
    // "//*[local-name()='TxInf']/*[local-name()='OrgnlGrpInf']/*[local-name()='OrgnlMsgNmId']");
    // data.put("msg_type", orgnlMsgNmId != null && !orgnlMsgNmId.isEmpty() ?
    // orgnlMsgNmId : "pacs.008");
    //
    // data.put("amount", getSafeXPathText(doc, xpath,
    // "//*[local-name()='TxInf']/*[local-name()='OrgnlIntrBkSttlmAmt']"));
    // String ccy = "";
    // try {
    // Element amtNode = (Element)
    // xpath.evaluate("//*[local-name()='TxInf']/*[local-name()='OrgnlIntrBkSttlmAmt']",
    // doc, XPathConstants.NODE);
    // if (amtNode != null) {
    // ccy = amtNode.getAttribute("Ccy");
    // }
    // } catch (Exception ignored) {}
    // data.put("currency", ccy.isEmpty() ? "AED" : ccy);
    //
    // data.put("dbtr_bic", getSafeXPathText(doc, xpath,
    // "//*[local-name()='TxInf']/*[local-name()='OrgnlTxRef']/*[local-name()='DbtrAgt']/*[local-name()='FinInstnId']/*[local-name()='BICFI']"));
    // data.put("cdtr_bic", getSafeXPathText(doc, xpath,
    // "//*[local-name()='TxInf']/*[local-name()='OrgnlTxRef']/*[local-name()='CdtrAgt']/*[local-name()='FinInstnId']/*[local-name()='BICFI']"));
    //
    // if (!isPerf) {
    // logger.info("=== Safely Extracted from PACS.004 ===");
    // for (Map.Entry<String, String> entry : data.entrySet()) {
    // logger.info(entry.getKey() + " = " + entry.getValue());
    // }
    // }
    // } catch (Exception e) {
    // logger.severe("Failed to parse pacs004: " + e.getMessage());
    // }
    // return data;
    // }

    private static void writeFile(String path, String content) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(f)) {
            fw.write(content);
        }
    }

    public static String processMessageString(String xmlText, Logger logger) throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(4));
        String ts = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")) + "_"
                + java.util.UUID.randomUUID().toString().substring(0, 8);
        String transDir = BASE_DIR + File.separator + "Files" + File.separator + ts;

        boolean isPerf = Config.getBoolean("high_perf_mode", false);
        String txSts = Config.getJSONObject("response_defaults").optString("TxSts", "ACCP");

        Map<String, String> extracted = new HashMap<>();
        String reqPath = "";
        String resPath = "";
        String responseXml = "";

        try {
            boolean isPacs002 = xmlText.contains("pacs.002") || xmlText.contains("FIToFIPmtStsRpt");
            if (isPacs002) {
                if (!isPerf)
                    logger.info("Received PACS.002 status report. Ignoring as it is not a request.");
                return null;
            }

            // boolean isPacs004 = xmlText.contains("pacs.004") ||
            // xmlText.contains("PmtRtr");
            boolean isPacs007 = xmlText.contains("pacs.007") || xmlText.contains("FIToFIPmtRvsl");
            boolean isPacs008 = xmlText.contains("pacs.008") && xmlText.contains("FIToFICstmrCdtTrf");
            // boolean isPacs028 = xmlText.contains("pacs.028") ||
            // xmlText.contains("FIToFIStsReq");

            // if (!isPacs004 && !isPacs007 && !isPacs008 && !isPacs028) {
            if (!isPacs007 && !isPacs008) {
                if (!isPerf)
                    logger.info("Ignored unsupported message type.");
                return null;
            }

            // String reqType = isPacs007 ? "pacs007" : (isPacs028 ? "pacs028" : (isPacs004
            // ? "pacs004" : "pacs008"));
            String reqType = isPacs007 ? "pacs007" : (isPacs008 ? "pacs008" : "Null");
            String resType = (isPacs007 && !txSts.equals("RJCT")) ? "pacs004" : "pacs002";

            reqPath = transDir + File.separator + "Request" + File.separator + reqType + ".xml";
            resPath = transDir + File.separator + "Response" + File.separator + resType + ".xml";

            if (!isPerf) {
                writeFile(reqPath, xmlText);
            }

            if (isPacs007) {
                extracted = parsePacs007(xmlText, logger, isPerf);
                extracted.put("tx_sts", txSts);

                if (txSts.equals("RJCT")) {
                    if (!isPerf)
                        logger.info("Building rejected PACS.002 response natively for PACS.007...");
                    responseXml = ResponseCreator.buildPacs002(extracted, isPerf ? null : logger, now);
                } else {
                    if (!isPerf)
                        logger.info("Building accepted PACS.004 response natively for PACS.007...");
                    responseXml = ResponseCreator.buildPacs004(extracted, isPerf ? null : logger, now);
                }
                // } else if (isPacs004) {
                // extracted = parsePacs004(xmlText, logger, isPerf);
                // extracted.put("tx_sts", txSts);
                //
                // if (!isPerf)
                // logger.info("Building PACS.002 response natively for PACS.004...");
                // responseXml = ResponseCreator.buildPacs002(extracted, isPerf ? null : logger,
                // now);
            } else {
                extracted = parsePacs008(xmlText, logger, isPerf);
                extracted.put("tx_sts", txSts);

                sendToStsServer(extracted, logger);

                CprBatchProcessor.addPacs008Transaction(extracted, logger);

                if (!isPerf)
                    logger.info("Building PACS.002 response natively for PACS.008...");
                responseXml = ResponseCreator.buildPacs002(extracted, isPerf ? null : logger, now);
            }

            if (!isPerf) {
                writeFile(resPath, responseXml);
                LoggerHelper.logRequestToExcel(extracted, reqPath, resPath, "SUCCESS", "");
            }

            return responseXml;

        } catch (Exception e) {
            if (!isPerf) {
                logger.severe("Error processing message: " + e.getMessage());
                LoggerHelper.logRequestToExcel(extracted, reqPath, resPath, "FAILED", e.getMessage());
            } else {
                logger.severe("Error processing message in performance mode: " + e.getMessage());
            }
            throw e;
        }
    }

    public static void sendToStsServer(Map<String, String> data, Logger logger) {
        try {
            if (!Config.getBoolean("enable_sts", true)) {
                return; // Skip if disabled in UI
            }

            // Configuration for STS server
            String hostname = "localhost"; // To be updated by user
            String port = "9191"; // To be updated by user

            if ("hostname".equals(hostname)) {
                return; // Skip if not configured
            }

            // Build the comma separated line
            // MsgId,CreDtTm,TtlRtrdIntrBkSttlmAmt,EndToEndId,TxId,UETR,ClrSysRef,InstgAgt<BICFI>,InstdAgt<BICFI>
            String line = String.join(",",
                    data.getOrDefault("msg_id", ""),
                    data.getOrDefault("cre_dt_tm", ""),
                    data.getOrDefault("amount", ""), // IntrBkSttlmAmt from pacs.008
                    data.getOrDefault("end_to_end_id", ""),
                    data.getOrDefault("tx_id", ""),
                    data.getOrDefault("uetr", ""),
                    data.getOrDefault("clr_sys_ref", ""),
                    data.getOrDefault("instg_bic", ""),
                    data.getOrDefault("instd_bic", ""));

            String encodedLine = URLEncoder.encode(line, StandardCharsets.UTF_8.toString());
            String addUrl = String.format("http://%s:%s/sts/ADD?FILENAME=STS_DataSet.csv&LINE=%s", hostname, port,
                    encodedLine);
            String saveUrl = String.format("http://%s:%s/sts/SAVE?FILENAME=STS_DataSet.csv", hostname, port);

            if (logger != null)
                logger.info("Sending pacs.008 data to STS server...");

            // Call ADD
            callUrl(addUrl, logger);
            // Call SAVE
            callUrl(saveUrl, logger);

        } catch (Exception e) {
            if (logger != null)
                logger.severe("Failed to send data to STS server: " + e.getMessage());
        }
    }

    private static void callUrl(String urlString, Logger logger) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int responseCode = conn.getResponseCode();
            if (responseCode != 200 && logger != null) {
                logger.warning("STS Server call to " + urlString + " returned " + responseCode);
            }
        } catch (Exception e) {
            if (logger != null)
                logger.severe("Error calling STS URL " + urlString + ": " + e.getMessage());
        }
    }
}