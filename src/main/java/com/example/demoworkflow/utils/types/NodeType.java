package com.example.demoworkflow.utils.types;

import com.example.demoworkflow.utils.workflow.dto.OutputVariableDes;
import com.example.demoworkflow.utils.workflow.nodes.*;
import com.example.demoworkflow.utils.workflow.nodes.base.*;
import com.example.demoworkflow.utils.workflow.nodes.collections.list.*;
import com.example.demoworkflow.utils.workflow.nodes.collections.map.*;
import com.example.demoworkflow.utils.workflow.nodes.collections.queue.*;
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
    WHILE("条件循环", 0x000008 | NodeType.NESTABLE_FLAG),
    BREAK("循环跳出", 0x000009),
    VARIABLE_ASSIGN("变量声明|赋值", 0x00000A),
    UNARY_OPERATORS("单目操作符", 0x00000B),
    BINARY_OPERATORS("双目操作符", 0x00000C),
    LIST_INDEX_GET("列表下标访问", 0x00000D),
    LIST_INDEX_SET("列表下标更新", 0x00000E),
    LIST_INDEX_REMOVE("列表下标删除", 0x00000F),
    LIST_ADD("列表添加元素", 0x000010),
    LIST_SIZE("列表长度", 0x000011),
    LIST_CLEAR("列表清空", 0x000012),
    MAP_GET("字典查询", 0x000013),
    MAP_PUT("字典写入", 0x000014),
    MAP_REMOVE("字典删键", 0x000015),
    MAP_CONTAINS("字典包含", 0x000016),
    // 队列
    QUEUE_PEEK("队列查询", 0x000017),
    QUEUE_PUSH("队列添加元素", 0x000018),
    QUEUE_POP("队列弹出", 0x000019),
    QUEUE_SIZE("队列长度", 0x00001A),
    QUEUE_CLEAR("队列清空", 0x00001B),
    // 函数
    FUNCTION_DEF("函数定义", 0x00001C | NodeType.NESTABLE_FLAG),
    FUNCTION_CALL("函数调用", 0x00001D),
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
        nodeClazzMap.put(WHILE.getCode(), WhileLoopNode.class);
        nodeClazzMap.put(BREAK.getCode(), BreakNode.class);
        nodeClazzMap.put(VARIABLE_ASSIGN.getCode(), VariableAssignNode.class);
        nodeClazzMap.put(UNARY_OPERATORS.getCode(), UnaryOperatorsNode.class);
        nodeClazzMap.put(BINARY_OPERATORS.getCode(), BinaryOperatorNode.class);
        nodeClazzMap.put(LIST_INDEX_GET.getCode(), ListIndexGetNode.class);
        nodeClazzMap.put(LIST_INDEX_SET.getCode(), ListIndexSetNode.class);
        nodeClazzMap.put(LIST_INDEX_REMOVE.getCode(), ListIndexRemoveNode.class);
        nodeClazzMap.put(LIST_ADD.getCode(), ListAddNode.class);
        nodeClazzMap.put(LIST_SIZE.getCode(), ListSizeNode.class);
        nodeClazzMap.put(LIST_CLEAR.getCode(), ListClearNode.class);
        nodeClazzMap.put(MAP_GET.getCode(), MapGetNode.class);
        nodeClazzMap.put(MAP_PUT.getCode(), MapPutNode.class);
        nodeClazzMap.put(MAP_REMOVE.getCode(), MapRemoveNode.class);
        nodeClazzMap.put(MAP_CONTAINS.getCode(), MapContainsNode.class);
        nodeClazzMap.put(QUEUE_PEEK.getCode(), QueuePeekNode.class);
        nodeClazzMap.put(QUEUE_PUSH.getCode(), QueueAddNode.class);
        nodeClazzMap.put(QUEUE_POP.getCode(), QueuePopNode.class);
        nodeClazzMap.put(QUEUE_SIZE.getCode(), QueueSizeNode.class);
        nodeClazzMap.put(QUEUE_CLEAR.getCode(), QueueClearNode.class);
        nodeClazzMap.put(FUNCTION_DEF.getCode(), FunctionDefNode.class);
        nodeClazzMap.put(FUNCTION_CALL.getCode(), FunctionCallNode.class);
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
