package com.mikle.syncup.ai.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiChatHistoryVO implements Serializable {

    private String sessionId;

    private List<AiChatMessageVO> messages = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
