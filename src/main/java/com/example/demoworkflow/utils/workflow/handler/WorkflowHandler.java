package com.example.demoworkflow.utils.workflow.handler;

import com.alibaba.fastjson2.JSON;
import com.example.demoworkflow.utils.workflow.dto.Workflow;
import com.example.demoworkflow.utils.workflow.nodes.NodeImpl;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.result.WorkflowResult;
import com.example.demoworkflow.utils.workflow.states.NodeStates;
import com.example.demoworkflow.utils.workflow.states.WorkflowStates;
import com.example.demoworkflow.vo.WorkflowVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class WorkflowHandler {
    @Resource
    private GlobalPool globalPool;

    @Resource
    private NodeHandler nodeHandler;

    @Resource
    private ResultHandler resultHandler;

    @Resource
    private SseHandler sseHandler;

    public String handler(WorkflowVO workflowVO){
        // 初始化流程
        Workflow workflow = Workflow.castFromVO(workflowVO, globalPool);
        handler(workflow);
        return workflow.getToken();
    }

    public void handler(Workflow workflow, HttpServletResponse rsp){
        log.info("开始处理流程");
        // 一开始处理就需要将当前工作流的过期时间设置好，防止其长时间存在
        globalPool.expire(workflow.getToken());
        resultHandler.run(workflow, rsp);
        if (globalPool.getWorkflowState(workflow.getToken()) == WorkflowStates.ERROR)
            return;
        globalPool.pushWorkflowResult(workflow.getToken(), WorkflowResult.builder()
                .token(workflow.getToken())
                .msg("开始执行流程图")
                .state(WorkflowStates.STAND_BY)
                .build());
        globalPool.workflowRunning(workflow.getToken());
        nodeHandler.run(workflow.getStartNode());
    }

    public void stopWorkflow(String token){
        globalPool.workflowError(token);
        globalPool.pushWorkflowResult(token, WorkflowResult.builder()
                    .token(token)
                    .msg("已终止流程图")
                    .state(WorkflowStates.ABORT)
                    .build());
    }

    /**
     * SSE流式返回的调用接口
     * @param workflow  待运行的工作流
     */
    @Async("workflow")
    public void handler(Workflow workflow){
        globalPool.expire(workflow.getToken());
        resultHandler.run(workflow, sseHandler);
        // 这个错误可能由前期预处理抛出，这种情况应当立刻停止后续工作，并通过结果处理线程返回错误信息
        if (globalPool.getWorkflowState(workflow.getToken()) == WorkflowStates.ERROR)
            return;
        globalPool.pushWorkflowResult(workflow.getToken(), WorkflowResult.builder()
                .token(workflow.getToken())
                .msg("开始执行流程图")
                .state(WorkflowStates.STAND_BY)
                .build());
        globalPool.workflowRunning(workflow.getToken());
        nodeHandler.run(workflow.getStartNode());
    }
}
