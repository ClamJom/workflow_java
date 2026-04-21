/**
 * 在此处实现节点的Vue-Flow样式配置
 */

import { markRaw } from 'vue';
import StartNode from './StartNode.vue';
import EndNode from './EndNode.vue';
import WorkNode from './WorkNode.vue';
import ConditionNode from './ConditionNode.vue';
import LoopNode from './LoopNode.vue';

// 用 markRaw 标记组件，防止 Vue 将其变为响应式对象（避免性能警告）
const StartNodeRaw = markRaw(StartNode);
const EndNodeRaw = markRaw(EndNode);
const WorkNodeRaw = markRaw(WorkNode);
const ConditionNodeRaw = markRaw(ConditionNode);
const LoopNodeRaw = markRaw(LoopNode);

export { StartNodeRaw as StartNode, EndNodeRaw as EndNode, WorkNodeRaw as WorkNode, ConditionNodeRaw as ConditionNode, LoopNodeRaw as LoopNode };

/**
 * 后端 NodeType 枚举 code 值
 * 与 com.example.demoworkflow.utils.types.NodeType 保持一致
 */
export const NESTABLE_FLAG = 0x010000;

export const NODE_TYPE_CODE = {
    EMPTY: 0x00000,
    START: 0x00001,
    END: 0x000002,
    HELLO: 0x000003,
    CONDITION: 0x000004,
    HTTP: 0x000005,
    /** 与 NodeType.LOOP 一致：0x000007 | NESTABLE_FLAG */
    LOOP: 0x000007 | NESTABLE_FLAG,
    WHILE_LOOP: 0x000008 | NESTABLE_FLAG,
    BREAK: 0x000009,
};

/**
 * 是否为可嵌套容器节点（当前仅 Loop，与后端 NESTABLE_FLAG 一致）
 * @param {number} code
 * @returns {boolean}
 */
export function isNestableNodeType(code) {
    return typeof code === 'number' && (code & NESTABLE_FLAG) !== 0;
}

/**
 * 根据后端节点 code 获取 Vue-Flow 自定义节点类型名称
 * @param {number} code - 后端 NodeType.code
 * @returns {string} Vue-Flow 节点类型名
 */
export function getVueFlowNodeType(code) {
    switch (code) {
        case NODE_TYPE_CODE.START:
            return 'start';
        case NODE_TYPE_CODE.END:
            return 'end';
        case NODE_TYPE_CODE.CONDITION:
            return 'condition';
        case NODE_TYPE_CODE.LOOP:
        case NODE_TYPE_CODE.WHILE_LOOP:
            return 'loop';
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
};
