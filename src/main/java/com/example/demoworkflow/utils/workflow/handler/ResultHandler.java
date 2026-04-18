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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class ResultHandler {
    @Value("${workflow.result-time-out}")
    private long resultTimeOut;

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

    @Async("workflow")
    public void run(Workflow workflow, SseHandler sseHandler){
        globalPool.initResultHandler(workflow.getToken());
        try{
            handler(workflow, sseHandler);
        }catch(Exception e){
            log.error("结果处理线程运行出错", e);
            globalPool.resultHandlerError(workflow.getToken());
            globalPool.workflowError(workflow.getToken());
        }
    }

    /**
     * 结果线程超时调用
     * @param token 工作流Token
     */
    private void handleResultTimeOut(String token){
            globalPool.pushWorkflowResult(token, WorkflowResult.builder()
                    .token(token)
                    .msg(String.format("获取工作流信息超时！超过%d秒没有收到任何输出", resultTimeOut / 1000))
                    .state(WorkflowStates.ERROR)
                    .build());
    }

    /**
     * 工作流结果处理线程
     * @param rsp   HTTP响应对象，向其中写入生成的结果
     * @throws IOException  写入结果时可能存在的IO异常
     */
    private void handler(Workflow workflow, HttpServletResponse rsp) throws IOException {
        globalPool.resultHandlerRunning(workflow.getToken());
        String token = workflow.getToken();
        long updateTime = System.currentTimeMillis();
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
            if(result == null) {
                if(System.currentTimeMillis() - updateTime > resultTimeOut){
                    handleResultTimeOut(token);
                    break;
                }
                continue;
            }
            updateTime = System.currentTimeMillis();
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
            else log.info("已获取到结果余量：{}", result.msg);
        }
        // 没有意义。实际上写入后马上就会被删除，此处保留
        globalPool.resultHandlerDone(workflow.getToken());
        // 最后清理变量池
        globalPool.deleteAll(token);
    }

    private void handler(Workflow workflow, SseHandler sseHandler){
        globalPool.resultHandlerRunning(workflow.getToken());
        String token = workflow.getToken();
        long updateTime = System.currentTimeMillis();
        while(true){
            WorkflowResult result = globalPool.pollWorkflowResult(token);
            if(workflow.isEnded() && result == null){
                globalPool.pushWorkflowResult(token, WorkflowResult.builder()
                        .token(token)
                        .msg("流程运行完毕")
                        .state(WorkflowStates.DONE)
                        .build());
                break;
            }
            if(result == null) {
                if(System.currentTimeMillis() - updateTime > resultTimeOut){
                    handleResultTimeOut(token);
                    break;
                }
                continue;
            }
            updateTime = System.currentTimeMillis();
            sseHandler.send(workflow.getToken(), JSON.toJSONString(result));
        }
        while(true){
            // 对剩余的Result进行清理
            WorkflowResult result = globalPool.pollWorkflowResult(token);
            if(result == null) break;
            sseHandler.send(workflow.getToken(), JSON.toJSONString(result));
        }
        // 没有意义。实际上写入后马上就会被删除，此处保留
        globalPool.resultHandlerDone(workflow.getToken());
        // 最后清理变量池
        globalPool.deleteAll(token);
        sseHandler.close(token);
    }
}
