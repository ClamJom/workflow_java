package com.example.demoworkflow.utils.workflow.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowResult {
    public String token;

    public String from = "system";

    public String nodeId;

    // 标记这条消息是否仅用于表示状态信息
    public boolean stateFlag;

    public int state;

    public String msg;

    public Object extData;
}
