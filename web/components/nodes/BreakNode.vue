<script setup>
import {Handle, Position} from "@vue-flow/core";
import {computed} from "vue";

const props = defineProps(["id", "data"]);

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
<div class="workflow-node break-node" :class="uiStateClass">
  <Handle type="target" :position="Position.Left" id="target" />
  <span class="node-label">{{ data?.wnode?.name || '跳出' }}</span>
  <Handle type="source" :position="Position.Right" id="source" />
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
  overflow: visible;
  transition: box-shadow 0.2s, border-color 0.2s;
}

.break-node {
  background-color: #722ed1;
  color: #fff;
  border-color: #531dab;
}

.node-label {
  pointer-events: none;
}

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
