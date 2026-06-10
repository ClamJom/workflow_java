<script setup>
import {Handle, Position} from "@vue-flow/core";
import {computed, inject} from "vue";

const props = defineProps(["id", "data"]);

const layoutDirection = inject('layoutDirection', 'horizontal');

const sourcePosition = computed(() =>
  layoutDirection.value === 'vertical' ? Position.Bottom : Position.Right
);

/**
 * 根据运行状态返回对应的 CSS 类名
 * runState: 0=就绪, 2=运行中, 256=完成, 512=失能, 1=错误
 */
const uiStateClass = computed(() => {
  if (props.data?.precheckError) return 'state-precheck-error';
  const state = props.data?.runState;
  if (state === 2) return 'state-running';
  if (state === 256) return 'state-done';
  if (state === 1) return 'state-error';
  if (state === 512) return 'state-disabled';
  return '';
});
</script>

<template>
<div class="workflow-node start-node" :class="uiStateClass">
  <span class="node-label">{{ data?.wnode?.name || '起始节点' }}</span>
  <Handle type="source" :position="sourcePosition" id="source" />
</div>
</template>

<style scoped>
.workflow-node {
  min-width: 120px;
  min-height: 40px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  border: 2px solid transparent;
  cursor: pointer;
  user-select: none;
  /* 确保 Handle 不被裁剪 */
  overflow: visible;
  transition: box-shadow 0.2s, border-color 0.2s;
}

.start-node {
  background-color: #52c41a;
  color: #fff;
  border-color: #389e0d;
}

.node-label {
  pointer-events: none;
}

/* 运行状态高亮 */
.state-running {
  border-color: #1677ff !important;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.3) !important;
}

.state-done {
  border-color: #52c41a !important;
  box-shadow: 0 0 0 3px rgba(82, 196, 26, 0.3) !important;
}

.state-error {
  border-color: #ff4d4f !important;
  box-shadow: 0 0 0 3px rgba(255, 77, 79, 0.3) !important;
}

.state-precheck-error {
  border-color: #ff4d4f !important;
  box-shadow: 0 0 0 3px rgba(255, 77, 79, 0.45) !important;
}

.state-disabled {
  opacity: 0.4;
}
</style>
