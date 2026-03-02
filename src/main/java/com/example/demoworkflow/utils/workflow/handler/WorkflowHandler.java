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

    public void handler(WorkflowVO workflowVO, HttpServletResponse rsp){
        // 初始化流程
        Workflow workflow = Workflow.castFromVO(workflowVO, globalPool);
        handler(workflow, rsp);
    }

    public void handler(Workflow workflow, HttpServletResponse rsp){
        log.info("开始处理流程");
        // 一开始处理就需要将当前工作流的过期时间设置好，防止其长时间存在
        globalPool.expire(workflow.getToken());
        resultHandler.run(workflow, rsp);
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
}
