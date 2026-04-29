package com.example.demoworkflow.utils.workflow.nodes;

import com.alibaba.fastjson2.JSON;
import com.example.demoworkflow.utils.types.ConfigTypes;
import com.example.demoworkflow.utils.types.NodeType;
import com.example.demoworkflow.utils.workflow.dto.OutputVariableDes;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.result.WorkflowResult;
import com.example.demoworkflow.vo.ConfigVO;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 网络请求节点
 */
public class HTTPRequestNode extends NodeImpl{

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko)";

    private OkHttpClient httpClient;
    private Request request;
    private boolean retryEnabled;
    private int maxRetriedTimes;

    public HTTPRequestNode(GlobalPool globalPool) {
        super(globalPool);
        setNodeType(NodeType.HTTP);
    }

    public HTTPRequestNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
        setNodeType(NodeType.HTTP);
    }

    @Override
    public List<ConfigVO> getNodeConfigs(){
        List<ConfigVO> defaultConfigs = new ArrayList<>();
        defaultConfigs.add(ConfigVO.builder()
                .name("Url")
                .type(ConfigTypes.STRING)
                .des("网络请求基础地址")
                .value("https://www.example.com")
                .required(true)
                .build());
        defaultConfigs.add(ConfigVO.builder()
                .name("Uri")
                .type(ConfigTypes.STRING)
                .des("资源地址")
                .required(false)
                .value("")
                .build());
        defaultConfigs.add(ConfigVO.builder()
                .name("Method")
                .type(ConfigTypes.SELECT)
                .options(JSON.toJSONString(List.of("GET", "POST", "PUT", "DELETE", "PATCH")))
                .des("网络请求模式")
                .value("GET")
                .required(true)
                .build());
        defaultConfigs.add(ConfigVO.builder()
                .name("Headers")
                .type(ConfigTypes.MAP)
                .des("请求头")
                .value("{}")
                .required(false)
                .build());
        defaultConfigs.add(ConfigVO.builder()
                .name("Data")
                .type(ConfigTypes.MAP)
                .des("请求数据")
                .value("{}")
                .required(false)
                .build());
        defaultConfigs.add(ConfigVO.builder()
                .name("Timeout")
                .type(ConfigTypes.NUMBER)
                .des("超时时间（毫秒）")
                .value("5000")
                .min(1000)
                .max(30000)
                .required(false)
                .build());
        defaultConfigs.add(ConfigVO.builder()
                .name("Retry")
                .type(ConfigTypes.BOOLEAN)
                .des("是否重试")
                .value("true")
                .required(false)
                .build());
        defaultConfigs.add(ConfigVO.builder()
                .name("MaxRetriedTimes")
                .type(ConfigTypes.NUMBER)
                .des("最大重试次数")
                .value("5")
                .min(0)
                .max(30)
                .build());
        return defaultConfigs;
    }

    @Override
    public List<OutputVariableDes> getNodeOutputs() {
        return List.of(OutputVariableDes.builder()
                        .name("output")
                        .type("String")
                        .des("请求结果")
                .build());
    }

    private boolean hasRequestBody(String method){
        return "POST".equals(method) || "DELETE".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    @Override
    public void before(){
        String url = (String) configs.get("Url");
        String uri = (String) configs.get("Uri");
        String method = (String) configs.get("Method");
        Map<String, String> headers = (Map<String, String>) configs.computeIfAbsent("Headers", k -> new HashMap<>());
        Map<String, String> data = (Map<String, String>) configs.computeIfAbsent("Data", k -> new HashMap<>());
        int timeout = (int) configs.get("Timeout");
        retryEnabled = (boolean) configs.get("Retry");
        maxRetriedTimes = (int) configs.get("MaxRetriedTimes");
        // 处理URL
        if(url == null){
            onNodeError("URL不允许为空");
            return;
        }
        if(!url.endsWith("/")) {
            url = url + "/";
        }
        // 处理URI
        if(uri != null) {
            if (uri.startsWith("/")) uri = uri.substring(1);
            url += uri;
        }
        // 处理请求方法
        if(method == null){
            onNodeError("请求模式不允许为空");
            return;
        }
        method = method.toUpperCase();
        // 处理请求数据（复制后迭代，避免在遍历时修改 Map）
        for (Map.Entry<String, String> e : new HashMap<>(data).entrySet()) {
            data.put(e.getKey(), globalPool.parseConfig(e.getValue(), token));
        }

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();

        RequestBody body = null;
        if (hasRequestBody(method) && !data.isEmpty()) {
            body = RequestBody.create(JSON.toJSONString(data), JSON_MEDIA_TYPE);
        }

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT);
        for (String k : headers.keySet()) {
            builder.header(k, headers.get(k));
        }
        builder.method(method, body);

        this.request = builder.build();
    }

    @Override
    public void run(){
        if (httpClient == null || request == null) {
            return;
        }
        int maxTries = retryEnabled ? (1 + maxRetriedTimes) : 1;
        for (int attempt = 0; attempt < maxTries; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    if (retryEnabled && attempt < maxTries - 1) {
                        sleepRetryInterval();
                        continue;
                    }
                    onNodeError("HTTP " + response.code() + " " + response.message());
                    return;
                }
                String result = response.body() != null ? response.body().string() : "";
                nodePool.put("output", result);
                putWorkflowResult(WorkflowResult.builder()
                        .token(token)
                        .nodeId(nodeId)
                        .msg("请求结果")
                        .extData(result)
                        .build());
                return;
            } catch (IOException e) {
                if (retryEnabled && attempt < maxTries - 1) {
                    sleepRetryInterval();
                    continue;
                }
                onNodeError(e.getMessage());
                return;
            }
        }
    }

    private static void sleepRetryInterval() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
