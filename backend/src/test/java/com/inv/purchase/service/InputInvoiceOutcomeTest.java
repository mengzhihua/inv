package com.inv.purchase.service;

import com.inv.purchase.entity.InputInvoice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputInvoiceOutcomeTest {
    @Test
    void splitsPassHeaderMismatchAndChecksumFail() {
        InputInvoice pass = new InputInvoice();
        pass.setVerifyStatus("VERIFIED");
        pass.setStatus("NORMAL");
        assertEquals("PASS", InputInvoiceService.outcome(pass));

        InputInvoice header = new InputInvoice();
        header.setVerifyStatus("VERIFIED");
        header.setStatus("ABNORMAL");
        assertEquals("HEADER_MISMATCH", InputInvoiceService.outcome(header));

        InputInvoice checksum = new InputInvoice();
        checksum.setVerifyStatus("FAILED");
        checksum.setStatus("NORMAL");
        assertEquals("CHECKSUM_FAIL", InputInvoiceService.outcome(checksum));
    }
}
