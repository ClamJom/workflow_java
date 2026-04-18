package com.example.demoworkflow.utils.types;

import com.example.demoworkflow.utils.workflow.dto.OutputVariableDes;
import com.example.demoworkflow.utils.workflow.nodes.*;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.vo.ConfigVO;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public enum NodeType {
    // 注意别把最下方的分号删了！
    EMPTY_NODE("空白节点", 0x00000),
    START("起点", 0x00001),
    END("终点", 0x000002),
    HELLO("问候", 0x000003),
    CONDITION("条件", 0x000004),
    HTTP("网络请求", 0x000005),
    SLEEP("休眠", 0x000006),
    LOOP("循环", 0x000007 | NodeType.NESTABLE_FLAG),
    ;

    private final String name;
    private final int code;

    // 可嵌套节点标志位
    public static final int NESTABLE_FLAG = 0x010000;

    private static final Map<Integer, Class<?>> nodeClazzMap = new HashMap<>();

    static {
        nodeClazzMap.put(EMPTY_NODE.getCode(), NodeImpl.class);
        nodeClazzMap.put(START.getCode(), StartNode.class);
        nodeClazzMap.put(END.getCode(), EndNode.class);
        nodeClazzMap.put(HELLO.getCode(), HelloNode.class);
        nodeClazzMap.put(CONDITION.getCode(), ConditionNode.class);
        nodeClazzMap.put(HTTP.getCode(), HTTPRequestNode.class);
        nodeClazzMap.put(SLEEP.getCode(), SleepNode.class);
        nodeClazzMap.put(LOOP.getCode(), LoopNode.class);
    }

    NodeType(String name, int code){
        this.name = name;
        this.code = code;
    }

    /**
     * 通过节点代码直接创建节点对象
     * @param code  节点类型代码
     * @param globalPool    工作流全局变量池
     * @return  对象
     */
    public static NodeImpl createNodeInstanceByCode(int code, GlobalPool globalPool){
        try{
            Class<?> clazz = nodeClazzMap.get(code);
            if(clazz == null) return null;
            return (NodeImpl) clazz.getDeclaredConstructor(GlobalPool.class).newInstance(globalPool);
        }catch (Exception e){
            return null;
        }
    }

    /**
     * 通过节点代码直接创建节点对象
     * @param code  节点类型代码
     * @param globalPool    工作流全局变量池
     * @param nodeId    节点ID
     * @return  对象
     */
    public static NodeImpl createNodeInstanceWithNodeIdByCode(int code, GlobalPool globalPool, String nodeId){
        try{
            Class<?> clazz = nodeClazzMap.get(code);
            if(clazz == null) return null;
            return (NodeImpl) clazz.getDeclaredConstructor(GlobalPool.class, String.class).newInstance(globalPool, nodeId);
        }catch (Exception e){
            return null;
        }
    }

    /**
     * 通过节点代码获取节点配置
     * @param code  节点代码
     * @return  节点配置
     */
    public static List<ConfigVO> getNodeConfigsByCode(int code){
        NodeImpl node = createNodeInstanceByCode(code, null);
        if(node == null) return null;
        return node.getNodeConfigs();
    }

    /**
     * 通过节点代码获取节点输出变量信息
     * @param code  节点代码
     * @return  节点输出变量信息
     */
    public static List<OutputVariableDes> getNodeOutputsByCode(int code){
        NodeImpl node = createNodeInstanceByCode(code, null);
        if(node == null) return null;
        return node.getNodeOutputs();
    }
}
