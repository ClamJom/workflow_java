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

// 用 markRaw 标记组件，防止 Vue 将其变为响应式对象（避免性能警告）
const StartNodeRaw = markRaw(StartNode);
const EndNodeRaw = markRaw(EndNode);
const WorkNodeRaw = markRaw(WorkNode);
const ConditionNodeRaw = markRaw(ConditionNode);
const LoopNodeRaw = markRaw(LoopNode);
const BreakNodeRaw = markRaw(BreakNode);
const CommentNodeRaw = markRaw(CommentNode);

export {
    StartNodeRaw as StartNode,
    EndNodeRaw as EndNode,
    WorkNodeRaw as WorkNode,
    ConditionNodeRaw as ConditionNode,
    LoopNodeRaw as LoopNode,
    BreakNodeRaw as BreakNode,
    CommentNodeRaw as CommentNode,
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
    LOOP: 0x0000007 | NESTABLE_FLAG,
    WHILE_LOOP: 0x0000008 | NESTABLE_FLAG,
    BREAK: 0x0000009,
    VARIABLE_ASSIGN: 0x000000A,
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
};
