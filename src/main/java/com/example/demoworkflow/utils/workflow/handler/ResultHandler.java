package com.example.demoworkflow.utils.workflow.handler;

import com.alibaba.fastjson2.JSON;
import com.example.demoworkflow.utils.workflow.dto.Workflow;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.result.WorkflowResult;
import com.example.demoworkflow.utils.workflow.states.NodeStates;
import com.example.demoworkflow.utils.workflow.states.WorkflowStates;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class ResultHandler {
    @Resource
    private GlobalPool globalPool;

    private boolean isNodesEnded(Workflow workflow){
        if(workflow.getNodes().isEmpty()) return true;
        return workflow.getNodes().stream().allMatch(node->
           (globalPool.getNodeState(node.token, node.nodeId) & NodeStates.DONE) != 0 ||
           (globalPool.getNodeState(node.token, node.nodeId) & NodeStates.ERROR) != 0
        );
    }

    @Async("workflow")
    public void run(Workflow workflow, HttpServletResponse rsp){
        globalPool.initResultHandler(workflow.getToken());
        try {
            handler(workflow, rsp);
        }catch(Exception e){
            // 结果处理线程出错前端不可见，无需操作结果队列
            log.error("运行流程结果处理线程出错", e);
            globalPool.resultHandlerError(workflow.getToken());
            globalPool.workflowError(workflow.getToken());
        }
    }

    /**
     * 工作流结果处理线程
     * @param rsp   HTTP响应对象，向其中写入生成的结果
     * @throws IOException  写入结果时可能存在的IO异常
     */
    private void handler(Workflow workflow, HttpServletResponse rsp) throws IOException, InterruptedException {
        globalPool.resultHandlerRunning(workflow.getToken());
        String token = workflow.getToken();
        while(true){
            WorkflowResult result = globalPool.pollWorkflowResult(token);
            if(workflow.isEnded() && result == null
            ){
                globalPool.pushWorkflowResult(token, WorkflowResult.builder()
                        .token(token)
                        .msg("流程运行完毕")
                        .state(WorkflowStates.DONE)
                        .build());
                break;
            }
            if(result == null) continue;
            if(rsp != null) rsp.getOutputStream().write(JSON.toJSONBytes(result));
            else {
                log.info("已获取到结果：{}", result.msg);
                if(result.msg.equals("output")){
                    log.info("Outputs: {}", JSON.toJSONString(result.extData));
                }
            }
        }
        while(true){
            // 对剩余的Result进行清理
            WorkflowResult result = globalPool.pollWorkflowResult(token);
            if(result == null) break;
            if(rsp != null) rsp.getOutputStream().write(JSON.toJSONBytes(result));
            else log.info("已获取到结果：{}", result.msg);
        }
        // 没有意义。实际上写入后马上就会被删除，此处保留
        globalPool.resultHandlerDone(workflow.getToken());
        // 最后清理变量池
        globalPool.deleteAll(token);
    }
}
