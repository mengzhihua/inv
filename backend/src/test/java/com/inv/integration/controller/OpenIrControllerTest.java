package com.inv.integration.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class OpenIrControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void snapshotsIncludeInputInvoiceThenVerify() throws Exception {
        String snapshots = mockMvc.perform(get("/api/open/ir/snapshots")
                        .header("X-Api-Key", "test-open-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.system").value("INV"))
                .andReturn().getResponse().getContentAsString();
        JsonNode input = null;
        JsonNode draft = null;
        JsonNode submitted = null;
        JsonNode issued = null;
        for (JsonNode row : objectMapper.readTree(snapshots).get("data").get("snapshots")) {
            if ("INPUT_INVOICE".equals(row.path("dataType").asText())
                    && "10001001".equals(row.path("bizKey").asText())) {
                input = row;
            }
            if ("INVOICE_REQUEST".equals(row.path("dataType").asText())
                    && "REQ-IR-DRAFT".equals(row.path("bizKey").asText())) {
                draft = row;
            }
            if ("INVOICE_REQUEST".equals(row.path("dataType").asText())
                    && "REQ-IR-SUBMITTED".equals(row.path("bizKey").asText())) {
                submitted = row;
            }
            if ("SALES_INVOICE".equals(row.path("dataType").asText())
                    && "00002000".equals(row.path("bizKey").asText())) {
                issued = row;
            }
        }
        assertNotNull(input, "应包含进项发票快照");
        assertNotNull(draft, "应包含 DRAFT 开票申请");
        assertNotNull(submitted, "应包含 SUBMITTED 开票申请");
        assertNotNull(issued, "应包含已开具销项发票");
        org.junit.jupiter.api.Assertions.assertEquals("DRAFT", draft.path("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", submitted.path("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals("ISSUED", issued.path("status").asText());

        String verified = mockMvc.perform(post("/api/open/ir/actions")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INV_VERIFY_INPUT\",\"targetKey\":\"10001001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String status = objectMapper.readTree(verified).path("data").path("verifyStatus").asText();
        assertTrue("VERIFIED".equals(status) || "FAILED".equals(status), status);
        String result = objectMapper.readTree(verified).path("data").path("verifyResult").asText();
        assertTrue("PASS".equals(result) || "HEADER_MISMATCH".equals(result) || "CHECKSUM_FAIL".equals(result), result);

        mockMvc.perform(post("/api/open/ir/actions")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INV_APPROVE_REQUEST\",\"targetKey\":\"REQ-IR-SUBMITTED\","
                                + "\"idempotencyKey\":\"INV-APPROVE-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        mockMvc.perform(post("/api/open/ir/actions")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INV_APPROVE_REQUEST\",\"targetKey\":\"REQ-IR-SUBMITTED\","
                                + "\"idempotencyKey\":\"INV-APPROVE-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        String submitBody = "{\"targetKey\":\"REQ-IR-DRAFT\",\"idempotencyKey\":\"INV-SUB-1\"}";
        mockMvc.perform(post("/api/open/ir/submit-request")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        mockMvc.perform(post("/api/open/ir/submit-request")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        mockMvc.perform(post("/api/open/ir/match-input")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceNo\":\"10001002\",\"poNo\":\"PO-1\",\"poAmount\":3390,"
                                + "\"receivedQty\":1,\"invoiceQty\":1,\"grCode\":\"GR-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.matchStatus").value("MATCHED"))
                .andExpect(jsonPath("$.data.poNo").value("PO-1"))
                .andExpect(jsonPath("$.data.receiptNo").value("GR-1"));
        mockMvc.perform(post("/api/open/ir/actions")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INV_MATCH_INPUT\",\"targetKey\":\"10001003\",\"poAmount\":1,"
                                + "\"receivedQty\":2,\"invoiceQty\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.matchStatus").value("MISMATCH"));
    }

    @Test
    public void issueStaysSeparateFromApprove() throws Exception {
        mockMvc.perform(post("/api/open/ir/issue-request")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNo\":\"REQ-IR-APPROVED\",\"idempotencyKey\":\"INV-ISSUE-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ISSUED"));
        mockMvc.perform(post("/api/open/ir/issue-request")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestNo\":\"REQ-IR-APPROVED\",\"idempotencyKey\":\"INV-ISSUE-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ISSUED"));
    }
}
