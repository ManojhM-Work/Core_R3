package com.expleo.simulator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class ResponseCreator {

    private static String getOr(Map<String, String> data, String key, String def) {
        String val = data.get(key);
        return (val != null && !val.isEmpty()) ? val : def;
    }

    private static String uuid30() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 30);
    }

    public static String buildPacs002(Map<String, String> data, Logger logger, LocalDateTime processingTime) {
        if (processingTime == null) {
            processingTime = LocalDateTime.now(ZoneOffset.ofHours(4));
        }

        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            docFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            String ns = "urn:iso:std:iso:20022:tech:xsd:pacs.002.001.11";

            Element document = doc.createElementNS(ns, "Document");
            doc.appendChild(document);

            Element report = doc.createElementNS(ns, "FIToFIPmtStsRpt");
            document.appendChild(report);

            Element grpHdr = doc.createElementNS(ns, "GrpHdr");
            report.appendChild(grpHdr);

//            Element msgId = doc.createElementNS(ns, "MsgId");
//            msgId.setTextContent(uuid30());
//            grpHdr.appendChild(msgId);
            Element msgId = doc.createElementNS(ns, "MsgId");
//            String ts = processingTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String ts = processingTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            String randomId = String.valueOf((int) (Math.random() * 90000) + 10000);
            msgId.setTextContent("PT_R82_" + ts + "_" + randomId);
            grpHdr.appendChild(msgId);

            Element creDtTm = doc.createElementNS(ns, "CreDtTm");
            creDtTm.setTextContent(processingTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")));
            grpHdr.appendChild(creDtTm);

            Element instg = doc.createElementNS(ns, "InstgAgt");
            Element instg_fin = doc.createElementNS(ns, "FinInstnId");
            Element instg_bic = doc.createElementNS(ns, "BICFI");
            // instg_bic.setTextContent("AEPCAEA0");
            instg_bic.setTextContent(getOr(data, "cdtr_bic", ""));
            instg_fin.appendChild(instg_bic);
            instg.appendChild(instg_fin);
            grpHdr.appendChild(instg);

            Element instd = doc.createElementNS(ns, "InstdAgt");
            Element instd_fin = doc.createElementNS(ns, "FinInstnId");
            Element instd_bic = doc.createElementNS(ns, "BICFI");
            // instd_bic.setTextContent(getOr(data, "dbtr_bic", ""));
            instd_bic.setTextContent("AEPCAEA0");
            instd_fin.appendChild(instd_bic);
            instd.appendChild(instd_fin);
            grpHdr.appendChild(instd);

            Element org = doc.createElementNS(ns, "OrgnlGrpInfAndSts");
            report.appendChild(org);

            Element orgnlMsgId = doc.createElementNS(ns, "OrgnlMsgId");
            orgnlMsgId.setTextContent(getOr(data, "msg_id", ""));
            org.appendChild(orgnlMsgId);

            Element orgnlMsgNmId = doc.createElementNS(ns, "OrgnlMsgNmId");
            orgnlMsgNmId.setTextContent(getOr(data, "msg_type", "pacs.008"));
            org.appendChild(orgnlMsgNmId);

            Element orgnlCreDtTm = doc.createElementNS(ns, "OrgnlCreDtTm");
            orgnlCreDtTm.setTextContent(getOr(data, "cre_dt_tm", ""));
            org.appendChild(orgnlCreDtTm);

            Element orgnlNbOfTxs = doc.createElementNS(ns, "OrgnlNbOfTxs");
            orgnlNbOfTxs.setTextContent(getOr(data, "nb_of_txs", "1"));
            org.appendChild(orgnlNbOfTxs);

            String tx_sts = getOr(data, "tx_sts", "");
            if (tx_sts.isEmpty()) {
                tx_sts = Config.getJSONObject("response_defaults").optString("TxSts", "ACCP");
            }

            Element stsRsnInf1 = doc.createElementNS(ns, "StsRsnInf");
            Element rsn1 = doc.createElementNS(ns, "Rsn");
            Element prtry1 = doc.createElementNS(ns, "Prtry");
            prtry1.setTextContent(
                    tx_sts + processingTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")));
            rsn1.appendChild(prtry1);
            stsRsnInf1.appendChild(rsn1);
            org.appendChild(stsRsnInf1);

            Element nbOfTxs = doc.createElementNS(ns, "NbOfTxsPerSts");
            Element dtldNbOfTxs = doc.createElementNS(ns, "DtldNbOfTxs");
            dtldNbOfTxs.setTextContent("1");
            nbOfTxs.appendChild(dtldNbOfTxs);
            Element dtldSts = doc.createElementNS(ns, "DtldSts");
            dtldSts.setTextContent(tx_sts);
            nbOfTxs.appendChild(dtldSts);
            Element dtldCtrlSum = doc.createElementNS(ns, "DtldCtrlSum");
            dtldCtrlSum.setTextContent(getOr(data, "amount", "0"));
            nbOfTxs.appendChild(dtldCtrlSum);
            org.appendChild(nbOfTxs);

            Element tx = doc.createElementNS(ns, "TxInfAndSts");
            report.appendChild(tx);

            String msg_type = getOr(data, "msg_type", "pacs.008");
            Element txStsId = doc.createElementNS(ns, "StsId");
            if (msg_type.equals("pacs.008") || msg_type.equals("pacs.007") || msg_type.equals("pacs.003")) {
                txStsId.setTextContent(getOr(data, "msg_id", ""));
                // } else if (msg_type.equals("pacs.004")) {
                // txStsId.setTextContent("");
            } else {
                String id = getOr(data, "sts_id", "");
                txStsId.setTextContent(id.isEmpty() ? uuid30() : id);
            }
            tx.appendChild(txStsId);

            Element orgEndToEnd = doc.createElementNS(ns, "OrgnlEndToEndId");
            orgEndToEnd.setTextContent(getOr(data, "end_to_end_id", ""));
            tx.appendChild(orgEndToEnd);

            Element orgTxId = doc.createElementNS(ns, "OrgnlTxId");
            orgTxId.setTextContent(getOr(data, "tx_id", ""));
            tx.appendChild(orgTxId);

            Element orgUETR = doc.createElementNS(ns, "OrgnlUETR");
            orgUETR.setTextContent(getOr(data, "uetr", ""));
            tx.appendChild(orgUETR);

            String orgnl_clr_sys_ref = getOr(data, "orgnl_clr_sys_ref", "");
            if (!orgnl_clr_sys_ref.isEmpty()) {
                Element orgnlClrSysRefEl = doc.createElementNS(ns, "OrgnlClrSysRef");
                orgnlClrSysRefEl.setTextContent(orgnl_clr_sys_ref);
                tx.appendChild(orgnlClrSysRefEl);
            }

            Element stsRsnInf2 = doc.createElementNS(ns, "StsRsnInf");
            Element rsn2 = doc.createElementNS(ns, "Rsn");
            Element prtry2 = doc.createElementNS(ns, "Prtry");
            prtry2.setTextContent(
                    tx_sts + processingTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")));
            rsn2.appendChild(prtry2);
            stsRsnInf2.appendChild(rsn2);
            // Element addtlInf = doc.createElementNS(ns, "AddtlInf");
            // addtlInf.setTextContent(tx_sts);
            // stsRsnInf2.appendChild(addtlInf);
            tx.appendChild(stsRsnInf2);

            // Element accptncDtTm = doc.createElementNS(ns, "AccptncDtTm");
            // accptncDtTm.setTextContent(processingTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")));
            // tx.appendChild(accptncDtTm);

            String clr_sys_ref = getOr(data, "clr_sys_ref", "");
            Element clrSysRefEl = doc.createElementNS(ns, "ClrSysRef");
            clrSysRefEl.setTextContent(clr_sys_ref.isEmpty() ? uuid30() : clr_sys_ref);
            tx.appendChild(clrSysRefEl);

            Element orgRef = doc.createElementNS(ns, "OrgnlTxRef");
            tx.appendChild(orgRef);

            String currency = getOr(data, "currency", "AED");
            String amount = getOr(data, "amount", "0");
            Element intrBkAmt = doc.createElementNS(ns, "IntrBkSttlmAmt");
            intrBkAmt.setAttribute("Ccy", currency);
            intrBkAmt.setTextContent(amount);
            orgRef.appendChild(intrBkAmt);

            // String sttlm_dt = getOr(data, "sttlm_dt", "");
            // if (!sttlm_dt.isEmpty()) {
            // Element sttlmDtEl = doc.createElementNS(ns, "IntrBkSttlmDt");
            // sttlmDtEl.setTextContent(sttlm_dt);
            // orgRef.appendChild(sttlmDtEl);
            // }

            Element pmtTp = doc.createElementNS(ns, "PmtTpInf");
            orgRef.appendChild(pmtTp);

            if (!getOr(data, "svc_lvl_prtry", "").isEmpty() || !getOr(data, "svc_lvl_cd", "").isEmpty()) {
                Element svcLvl = doc.createElementNS(ns, "SvcLvl");
                if (!getOr(data, "svc_lvl_prtry", "").isEmpty()) {
                    Element p = doc.createElementNS(ns, "Prtry");
                    p.setTextContent(data.get("svc_lvl_prtry"));
                    svcLvl.appendChild(p);
                } else {
                    Element c = doc.createElementNS(ns, "Cd");
                    c.setTextContent(data.get("svc_lvl_cd"));
                    svcLvl.appendChild(c);
                }
                pmtTp.appendChild(svcLvl);
            }

            Element lclIn = doc.createElementNS(ns, "LclInstrm");
            if (!getOr(data, "lcl_instrm_prtry", "").isEmpty()) {
                Element p = doc.createElementNS(ns, "Prtry");
                p.setTextContent(data.get("lcl_instrm_prtry"));
                lclIn.appendChild(p);
            } else if (!getOr(data, "lcl_instrm_cd", "").isEmpty()) {
                Element c = doc.createElementNS(ns, "Cd");
                c.setTextContent(data.get("lcl_instrm_cd"));
                lclIn.appendChild(c);
            } else {
                Element p = doc.createElementNS(ns, "Prtry");
                p.setTextContent("NUGT");
                lclIn.appendChild(p);
            }
            pmtTp.appendChild(lclIn);

            Element dbtrAgt = doc.createElementNS(ns, "DbtrAgt");
            Element dbtrFin = doc.createElementNS(ns, "FinInstnId");
            Element dbtrBic = doc.createElementNS(ns, "BICFI");
            dbtrBic.setTextContent(getOr(data, "dbtr_bic", ""));
            dbtrFin.appendChild(dbtrBic);
            dbtrAgt.appendChild(dbtrFin);
            orgRef.appendChild(dbtrAgt);

            Element cdtrAgt = doc.createElementNS(ns, "CdtrAgt");
            Element cdtrFin = doc.createElementNS(ns, "FinInstnId");
            Element cdtrBic = doc.createElementNS(ns, "BICFI");
            cdtrBic.setTextContent(getOr(data, "cdtr_bic", ""));
            cdtrFin.appendChild(cdtrBic);
            cdtrAgt.appendChild(cdtrFin);
            orgRef.appendChild(cdtrAgt);

            if (logger != null) {
                logger.info("PACS.002 Document successfully generated for OrgnlMsgId: " + data.get("msg_id"));
            }

            return domToString(doc);
        } catch (Exception e) {
            if (logger != null)
                logger.severe("Error building pacs.002: " + e.getMessage());
            return "";
        }
    }

    public static String buildPacs004(Map<String, String> data, Logger logger, LocalDateTime processingTime) {
        if (processingTime == null) {
            processingTime = LocalDateTime.now(ZoneOffset.ofHours(4));
        }

        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            docFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            String ns = "urn:iso:std:iso:20022:tech:xsd:pacs.004.001.11";

            String mndtId = data.get("mndt_id");
            String dbtrBicVal = "oooooooo";
            String cdtrBicVal = "oooooooo";
            if (mndtId != null && mndtId.length() >= 16) {
                dbtrBicVal = mndtId.substring(0, 8);
                cdtrBicVal = mndtId.substring(8, 16);
            }

            Element document = doc.createElementNS(ns, "Document");
            // document.setAttribute("xmlns:pacs", ns);
            doc.appendChild(document);

            Element report = doc.createElementNS(ns, "PmtRtr");
            document.appendChild(report);

            Element grpHdr = doc.createElementNS(ns, "GrpHdr");
            report.appendChild(grpHdr);

            // MsgId Pattern: Athi_RET_yyyyMMddHHmmss_RANDOM
            Element msgId = doc.createElementNS(ns, "MsgId");
//            String ts = processingTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String ts = processingTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmSSSS"));
            String randomId = String.valueOf((int) (Math.random() * 90000) + 10000);
            msgId.setTextContent("PT_RET" + ts + "_" + randomId);
            grpHdr.appendChild(msgId);

            Element creDtTm = doc.createElementNS(ns, "CreDtTm");
            creDtTm.setTextContent(
                    processingTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS+04:00")));
            grpHdr.appendChild(creDtTm);

            Element nbOfTxs = doc.createElementNS(ns, "NbOfTxs");
            nbOfTxs.setTextContent("1");
            grpHdr.appendChild(nbOfTxs);

            String currency = getOr(data, "currency", "AED");
            String amount = getOr(data, "amount", "1.00");
            Element ttlAmt = doc.createElementNS(ns, "TtlRtrdIntrBkSttlmAmt");
            ttlAmt.setAttribute("Ccy", currency);
            ttlAmt.setTextContent(amount);
            grpHdr.appendChild(ttlAmt);

            String sttlm_dt = getOr(data, "sttlm_dt", processingTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            Element sttlmDt = doc.createElementNS(ns, "IntrBkSttlmDt");
            sttlmDt.setTextContent(sttlm_dt);
            grpHdr.appendChild(sttlmDt);

            Element sttlmInf = doc.createElementNS(ns, "SttlmInf");
            Element sttlmMtd = doc.createElementNS(ns, "SttlmMtd");
            sttlmMtd.setTextContent("CLRG");
            sttlmInf.appendChild(sttlmMtd);
            Element clrSys = doc.createElementNS(ns, "ClrSys");
            Element clrSysPrtry = doc.createElementNS(ns, "Prtry");
            clrSysPrtry.setTextContent("Aani Core Service");
            clrSys.appendChild(clrSysPrtry);
            sttlmInf.appendChild(clrSys);
            grpHdr.appendChild(sttlmInf);

            Element instg = doc.createElementNS(ns, "InstgAgt");
            Element instg_fin = doc.createElementNS(ns, "FinInstnId");
            Element instg_bic = doc.createElementNS(ns, "BICFI");
            instg_bic.setTextContent(cdtrBicVal); // Based on user sample
            // instg_bic.setTextContent(getOr(data, "instg_bic", ""));
            instg_fin.appendChild(instg_bic);
            instg.appendChild(instg_fin);
            grpHdr.appendChild(instg);

            Element instd = doc.createElementNS(ns, "InstdAgt");
            Element instd_fin = doc.createElementNS(ns, "FinInstnId");
            Element instd_bic = doc.createElementNS(ns, "BICFI");
            instd_bic.setTextContent("AEPCAEA0"); // Based on user sample
            instd_fin.appendChild(instd_bic);
            instd.appendChild(instd_fin);
            grpHdr.appendChild(instd);

            Element tx = doc.createElementNS(ns, "TxInf");
            report.appendChild(tx);

            // RtrId Pattern: RTR_PTID_yyyyMMddHHmmss_0
            Element rtrId = doc.createElementNS(ns, "RtrId");
            rtrId.setTextContent("RTR_PTID_" + ts + "_0");
            tx.appendChild(rtrId);

            Element orgGrp = doc.createElementNS(ns, "OrgnlGrpInf");
            Element orgMsgId = doc.createElementNS(ns, "OrgnlMsgId");
            orgMsgId.setTextContent(getOr(data, "orgnl_msg_id", ""));
            orgGrp.appendChild(orgMsgId);
            Element orgMsgNmId = doc.createElementNS(ns, "OrgnlMsgNmId");
            orgMsgNmId.setTextContent(getOr(data, "orgnl_msg_nm_id", "pacs.008"));
            orgGrp.appendChild(orgMsgNmId);
            tx.appendChild(orgGrp);

            Element orgEnd = doc.createElementNS(ns, "OrgnlEndToEndId");
            orgEnd.setTextContent(getOr(data, "orgnl_end_to_end_id", ""));
            tx.appendChild(orgEnd);

            Element orgTx = doc.createElementNS(ns, "OrgnlTxId");
            orgTx.setTextContent(getOr(data, "orgnl_tx_id", ""));
            tx.appendChild(orgTx);

            Element orgUetr = doc.createElementNS(ns, "OrgnlUETR");
            orgUetr.setTextContent(getOr(data, "orgnl_uetr", ""));
            tx.appendChild(orgUetr);

            String clr_sys_ref = getOr(data, "orgnl_clr_sys_ref", "");
            if (!clr_sys_ref.isEmpty()) {
                Element orgClrRef = doc.createElementNS(ns, "OrgnlClrSysRef");
                orgClrRef.setTextContent(clr_sys_ref);
                tx.appendChild(orgClrRef);
            }

            Element orgAmt = doc.createElementNS(ns, "OrgnlIntrBkSttlmAmt");
            orgAmt.setAttribute("Ccy", currency);
            orgAmt.setTextContent(amount);
            tx.appendChild(orgAmt);

            // PmtTpInf/LclInstrm/Prtry = INST
            Element pmtTp = doc.createElementNS(ns, "PmtTpInf");
            Element lcl = doc.createElementNS(ns, "LclInstrm");
            Element lstPrtry = doc.createElementNS(ns, "Prtry");
            lstPrtry.setTextContent("INST");
            lcl.appendChild(lstPrtry);
            pmtTp.appendChild(lcl);
            tx.appendChild(pmtTp);

            Element rtrAmt = doc.createElementNS(ns, "RtrdIntrBkSttlmAmt");
            rtrAmt.setAttribute("Ccy", currency);
            rtrAmt.setTextContent(amount);
            tx.appendChild(rtrAmt);

            Element rsnInf = doc.createElementNS(ns, "RtrRsnInf");
            Element rsn = doc.createElementNS(ns, "Rsn");
            Element rsnCd = doc.createElementNS(ns, "Cd");
            rsnCd.setTextContent(getOr(data, "rsn_cd", "AM05"));
            rsn.appendChild(rsnCd);
            rsnInf.appendChild(rsn);
            tx.appendChild(rsnInf);

            Element orgRef = doc.createElementNS(ns, "OrgnlTxRef");

            Element dbtr = doc.createElementNS(ns, "DbtrAgt");
            Element dbtrFin = doc.createElementNS(ns, "FinInstnId");
            Element dbtrBic = doc.createElementNS(ns, "BICFI");
            // dbtrBic.setTextContent(getOr(data, "instd_bic", ""));
            dbtrBic.setTextContent(dbtrBicVal);
            dbtrFin.appendChild(dbtrBic);
            dbtr.appendChild(dbtrFin);
            orgRef.appendChild(dbtr);

            Element cdtr = doc.createElementNS(ns, "CdtrAgt");
            Element cdtrFin = doc.createElementNS(ns, "FinInstnId");
            Element cdtrBic = doc.createElementNS(ns, "BICFI");
            cdtrBic.setTextContent(cdtrBicVal); // Based on user sample
            // cdtrBic.setTextContent(getOr(data, "instg_bic", ""));
            cdtrFin.appendChild(cdtrBic);
            cdtr.appendChild(cdtrFin);
            orgRef.appendChild(cdtr);

            tx.appendChild(orgRef);

            if (logger != null) {
                logger.info("PACS.004 Document successfully generated for OrgnlMsgId: " + data.get("orgnl_msg_id"));
            }

            return domToString(doc);
        } catch (Exception e) {
            if (logger != null)
                logger.severe("Error building pacs.004: " + e.getMessage());
            return "";
        }
    }

    private static String domToString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(
                "{https://urldefense.com/v3/__http://xml.apache.org/xslt*7Dindent-amount__;JQ!!KEBONX3EBw!f6qaGqTNfBRa8oMiY7gvfRN674BFFyaa57R33jOl10u8qImcLOcaBtcnbsrF_Db0MMUuXQG7WdoWcqyizSPphTDI6sDWgak$ ",
                "4");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + writer.toString();
    }
}