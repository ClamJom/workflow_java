package com.example.demoworkflow.utils.workflow.nodes;

import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.vo.ConfigVO;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络请求节点
 */
public class HTTPRequestNode extends NodeImpl{

    private WebClient webClient;

    HTTPRequestNode(GlobalPool globalPool) {
        super(globalPool);
        setNodeType(NodeType.HTTP);
    }

    HTTPRequestNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        setNodeType(NodeType.HTTP);
    }

    @Override
    public List<ConfigVO> getNodeConfigs(){
        List<ConfigVO> defaultConfigs = new ArrayList<>();
        defaultConfigs.add(ConfigVO.builder()
                .name("Url")
                .type("String")
                .des("网络请求基础地址")
                .value("https://www.example.com")
                .required(true)
                .build());
        defaultConfigs.add(ConfigVO.builder()
                .name("Uri")
                .type("String")
                .des("资源地址")
                .required(false)
                .value("")
                .build());
        defaultConfigs.add(ConfigVO.builder()
                .name("Method")
                .type("String")
                .des("网络请求模式")
                .value("GET")
                .required(true)
                .build());
        defaultConfigs.add(ConfigVO.builder()
                .name("Headers")
                .type("Map")
                .des("请求头")
                .value("{}")
                .required(false)
                .build());
        defaultConfigs.add(ConfigVO.builder()
                .name("Data")
                .type("Map")
                .des("请求数据")
                .value("{}")
                .required(false)
                .build());
        defaultConfigs.add(ConfigVO.builder()
                .name("Timeout")
                .type("String")
                .des("超时时间（秒）")
                .value("10000")
                .required(false)
                .build());
        defaultConfigs.add(ConfigVO.builder()
                .name("Retry")
                .type("Boolean")
                .des("是否重试")
                .value("true")
                .required(false)
                .build());
        return defaultConfigs;
    }

    @Override
    public void before(){
        webClient = WebClient.create((String) configs.get("Url"));
        // TODO: 处理配置并初始化
    }

    @Override
    public void run(){
        String method = (String) configs.get("Method");
        method = method.toLowerCase();
        // TODO: 请求并返回结果
    }
}
