package com.inv.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inv.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/** 向 SRM 取采购订单金额和已收数量。默认关闭，匹配时使用请求里带来的数字。 */
@Component
public class SrmMatchClient {
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${inv.srm.mode:off}")
    private String mode;
    @Value("${inv.srm.base-url:http://localhost:8087}")
    private String baseUrl;
    @Value("${inv.srm.api-key:srm-wms-key}")
    private String apiKey;

    public Map<String, Object> basis(String poCode) {
        if (!"http".equalsIgnoreCase(mode) || poCode == null || poCode.trim().isEmpty()) {
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", apiKey);
        try {
            ResponseEntity<String> response = rest.exchange(
                    baseUrl + "/api/open/ir/match-basis?poCode=" + poCode.trim(),
                    HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
            if (root.path("code").asInt(-1) != 0) {
                throw new BizException("SRM 匹配依据失败: " + response.getBody());
            }
            JsonNode data = root.path("data");
            Map<String, Object> basis = new LinkedHashMap<String, Object>();
            basis.put("poAmount", data.path("poAmount").isMissingNode() || data.path("poAmount").isNull()
                    ? null : data.path("poAmount").decimalValue());
            basis.put("receivedQty", data.path("receivedQty").isMissingNode() || data.path("receivedQty").isNull()
                    ? null : data.path("receivedQty").decimalValue());
            basis.put("grCode", data.path("grCode").isMissingNode() || data.path("grCode").isNull()
                    ? null : data.path("grCode").asText());
            return basis;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("读取 SRM 匹配依据失败: " + e.getMessage());
        }
    }
}
