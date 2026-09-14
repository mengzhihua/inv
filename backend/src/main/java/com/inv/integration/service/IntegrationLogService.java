package com.inv.integration.service;

import com.inv.integration.entity.IntegrationLog;
import com.inv.integration.mapper.IntegrationLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IntegrationLogService {
    private final IntegrationLogMapper mapper;
    private final ObjectMapper objectMapper;

    /** 独立事务记录入站调用，即使业务失败也保留日志 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void inbound(String target, String action, String refNo, Object request, Object response, String error) {
        IntegrationLog l = new IntegrationLog();
        l.setDirection("IN");
        l.setTarget(target);
        l.setAction(action);
        l.setRefNo(refNo);
        l.setRequestBody(json(request));
        l.setResponseBody(json(response));
        l.setSuccess(error == null ? 1 : 0);
        l.setErrorMsg(error == null ? null : (error.length() > 500 ? error.substring(0, 500) : error));
        mapper.insert(l);
    }

    private String json(Object o) {
        if (o == null) {
            return null;
        }
        try {
            String s = objectMapper.writeValueAsString(o);
            return s.length() > 4000 ? s.substring(0, 4000) : s;
        } catch (JsonProcessingException e) {
            return String.valueOf(o);
        }
    }
}
