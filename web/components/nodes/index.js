/**
 * 在此处实现节点的Vue-Flow样式配置
 */

import { markRaw } from 'vue';
import StartNode from './StartNode.vue';
import EndNode from './EndNode.vue';
import WorkNode from './WorkNode.vue';
import ConditionNode from './ConditionNode.vue';
import LoopNode from './LoopNode.vue';
import BreakNode from './BreakNode.vue';
import CommentNode from './CommentNode.vue';
import FunctionDefNode from './FunctionDefNode.vue';
import FunctionCallNode from './FunctionCallNode.vue';

// 用 markRaw 标记组件，防止 Vue 将其变为响应式对象（避免性能警告）
const StartNodeRaw = markRaw(StartNode);
const EndNodeRaw = markRaw(EndNode);
const WorkNodeRaw = markRaw(WorkNode);
const ConditionNodeRaw = markRaw(ConditionNode);
const LoopNodeRaw = markRaw(LoopNode);
const BreakNodeRaw = markRaw(BreakNode);
const CommentNodeRaw = markRaw(CommentNode);
const FunctionDefNodeRaw = markRaw(FunctionDefNode);
const FunctionCallNodeRaw = markRaw(FunctionCallNode);

export {
    StartNodeRaw as StartNode,
    EndNodeRaw as EndNode,
    WorkNodeRaw as WorkNode,
    ConditionNodeRaw as ConditionNode,
    LoopNodeRaw as LoopNode,
    BreakNodeRaw as BreakNode,
    CommentNodeRaw as CommentNode,
    FunctionDefNodeRaw as FunctionDefNode,
    FunctionCallNodeRaw as FunctionCallNode,
};

/**
 * 后端 NodeType 枚举 code 值
 * 与 com.example.demoworkflow.utils.types.NodeType 保持一致
 */
export const NESTABLE_FLAG = 0x010000;

export const NODE_TYPE_CODE = {
    /** 注释节点（与后端 NodeType.EMPTY_NODE code 0 一致，画布上作注释用） */
    COMMENT: 0x000000,
    START: 0x000001,
    END: 0x0000002,
    HELLO: 0x0000003,
    CONDITION: 0x0000004,
    HTTP: 0x000005,
    SLEEP: 0x000006,
    LOOP: 0x0000007 | NESTABLE_FLAG,
    WHILE_LOOP: 0x0000008 | NESTABLE_FLAG,
    BREAK: 0x0000009,
    VARIABLE_ASSIGN: 0x000000A,
    UNARY_OPERATORS: 0x000000B,
    BINARY_OPERATORS: 0x000000C,
    LIST_INDEX_GET: 0x000000D,
    LIST_INDEX_SET: 0x000000E,
    LIST_INDEX_REMOVE: 0x000000F,
    LIST_ADD: 0x000010,
    LIST_SIZE: 0x000011,
    LIST_CLEAR: 0x000012,
    MAP_GET: 0x000013,
    MAP_PUT: 0x000014,
    MAP_REMOVE: 0x000015,
    MAP_CONTAINS: 0x000016,
    QUEUE_PEEK: 0x000017,
    QUEUE_PUSH: 0x000018,
    QUEUE_POP: 0x000019,
    QUEUE_SIZE: 0x00001A,
    QUEUE_CLEAR: 0x00001B,
    FUNCTION_DEF: 0x00001C | NESTABLE_FLAG,
    FUNCTION_CALL: 0x00001D,
};

/**
 * 是否为可嵌套容器节点
 * @param {number} code
 * @returns {boolean}
 */
export function isNestableNodeType(code) {
    return typeof code === 'number' && (code & NESTABLE_FLAG) !== 0;
}

/**
 * 是否为注释节点（无连线、无配置，仅展示说明文字）
 * @param {number} code
 * @returns {boolean}
 */
export function isCommentNodeType(code) {
    return code === NODE_TYPE_CODE.COMMENT;
}

/**
 * 前端展示名映射。后端 HELLO 节点实际用于输出消息，界面展示为「输出」。
 * @param {{code?: number, name?: string}} nodeType
 * @returns {string}
 */
export function getNodeDisplayName(nodeType) {
    if (nodeType?.code === NODE_TYPE_CODE.HELLO) return '输出';
    return nodeType?.name || '';
}

/**
 * 根据后端节点 code 获取 Vue-Flow 自定义节点类型名称
 * @param {number} code - 后端 NodeType.code
 * @returns {string} Vue-Flow 节点类型名
 */
export function getVueFlowNodeType(code) {
    switch (code) {
        case NODE_TYPE_CODE.COMMENT:
            return 'comment';
        case NODE_TYPE_CODE.START:
            return 'start';
        case NODE_TYPE_CODE.END:
            return 'end';
        case NODE_TYPE_CODE.CONDITION:
            return 'condition';
        case NODE_TYPE_CODE.LOOP:
        case NODE_TYPE_CODE.WHILE_LOOP:
            return 'loop';
        case NODE_TYPE_CODE.BREAK:
            return 'break';
        case NODE_TYPE_CODE.FUNCTION_DEF:
            return 'function-def';
        case NODE_TYPE_CODE.FUNCTION_CALL:
            return 'function-call';
        default:
            return 'work';
    }
}

/**
 * Vue-Flow nodeTypes 映射对象，传入 VueFlow 组件的 :node-types 属性
 */
export const nodeTypes = {
    start: StartNode,
    end: EndNode,
    work: WorkNode,
    condition: ConditionNode,
    loop: LoopNode,
    break: BreakNode,
    comment: CommentNode,
    'function-def': FunctionDefNode,
    'function-call': FunctionCallNode,
};
