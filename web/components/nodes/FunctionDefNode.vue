<script setup>
import {Handle, Position, useVueFlow} from '@vue-flow/core';
import {NodeResizer} from '@vue-flow/node-resizer';
import {computed, nextTick, inject} from 'vue';
import '@vue-flow/node-resizer/dist/style.css';

const props = defineProps(['id', 'data']);

const layoutDirection = inject('layoutDirection', 'horizontal');

const targetPosition = computed(() =>
  layoutDirection.value === 'vertical' ? Position.Top : Position.Left
);
const sourcePosition = computed(() =>
  layoutDirection.value === 'vertical' ? Position.Bottom : Position.Right
);

const {setNodes, updateNodeInternals} = useVueFlow();

const uiStateClass = computed(() => {
  if (props.data?.precheckError) return 'state-precheck-error';
  const state = props.data?.runState;
  if (state === 2) return 'state-running';
  if (state === 256) return 'state-done';
  if (state === 1) return 'state-error';
  if (state === 512) return 'state-disabled';
  return '';
});

/**
 * @param {{ params: { width: number, height: number } }} ev
 */
function onResizeEnd(ev) {
  const params = ev?.params;
  if (!params) return;
  const w = Math.round(params.width);
  const h = Math.round(params.height);
  setNodes((nodes) => nodes.map((n) => {
    if (n.id !== props.id) return n;
    return {
      ...n,
      style: {
        ...(n.style || {}),
        width: `${w}px`,
        height: `${h}px`,
      },
    };
  }));
  nextTick(() => updateNodeInternals(props.id));
}
</script>

<template>
  <div class="funcdef-root">
    <NodeResizer
      class="funcdef-node-resizer"
      :min-width="280"
      :min-height="200"
      @resize-end="onResizeEnd"
    />
    <div class="funcdef-shell" :class="uiStateClass">
      <Handle type="target" :position="targetPosition" id="target" class="funcdef-handle"
              :class="layoutDirection === 'vertical' ? 'funcdef-handle-top' : 'funcdef-handle-left'" />
      <div class="funcdef-chrome">
        <span class="funcdef-title">{{ data?.wnode?.name || '函数定义' }}</span>
      </div>
      <div class="funcdef-body">
        <div class="funcdef-drop-area" aria-hidden="true">
          <span class="funcdef-drop-hint">函数体 · 可将节点拖入。参数在内部「开始」节点中配置</span>
        </div>
      </div>
      <Handle type="source" :position="sourcePosition" id="source" class="funcdef-handle"
              :class="layoutDirection === 'vertical' ? 'funcdef-handle-bottom' : 'funcdef-handle-right'" />
    </div>
  </div>
</template>

<style scoped>
.funcdef-root {
  width: 100%;
  height: 100%;
  position: relative;
}

.funcdef-root :deep(.vue-flow__resize-control) {
  opacity: 0;
  transition: all 0.3s;
}
.funcdef-root :deep(.vue-flow__resize-control *) {
  opacity: 0 !important;
  stroke: transparent !important;
  fill: transparent !important;
}
.funcdef-root:hover :deep(.vue-flow__resize-control){
  opacity: 0.5;
}
.funcdef-root:hover :deep(.vue-flow__resize-control *){
  opacity: 0.5;
  stroke: unset;
  fill: #096dd9;
}

.funcdef-shell {
  width: 100%;
  height: 100%;
  min-width: 280px;
  min-height: 200px;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  box-sizing: border-box;
  overflow: visible;
  cursor: grab;
  user-select: none;
  border: 2px solid #096dd9;
  background: rgba(230, 244, 255, 0.75);
  box-shadow: 0 2px 12px rgba(9, 109, 217, 0.12);
  transition: box-shadow 0.2s, border-color 0.2s;
  pointer-events: none;
}

.funcdef-chrome {
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 8px 28px;
  background: rgba(255, 255, 255, 0.88);
  border-bottom: 1px solid rgba(9, 109, 217, 0.2);
  border-radius: 10px 10px 0 0;
  min-height: 40px;
  pointer-events: auto;
}

.funcdef-title {
  font-size: 13px;
  font-weight: 600;
  color: #003a8c;
  pointer-events: none;
  text-align: center;
}

.funcdef-handle {
  position: absolute !important;
}

.funcdef-handle-left {
  left: -6px !important;
  top: 50% !important;
  transform: translateY(-50%) !important;
}

.funcdef-handle-right {
  right: -6px !important;
  top: 50% !important;
  transform: translateY(-50%) !important;
}

.funcdef-handle-top {
  top: -6px !important;
  left: 50% !important;
  transform: translateX(-50%) !important;
}

.funcdef-handle-bottom {
  bottom: -6px !important;
  left: 50% !important;
  transform: translateX(-50%) !important;
}

.funcdef-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 120px;
  margin: 8px;
  gap: 8px;
}

.funcdef-params-hint {
  flex-shrink: 0;
  font-size: 11px;
  color: #096dd9;
  background: rgba(9, 109, 217, 0.08);
  padding: 4px 10px;
  border-radius: 6px;
  text-align: center;
  pointer-events: none;
}

.funcdef-drop-area {
  flex: 1;
  min-height: 80px;
  border-radius: 8px;
  border: 1px dashed rgba(9, 109, 217, 0.35);
  background: rgba(9, 109, 217, 0.05);
  pointer-events: none;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 8px;
}

.funcdef-drop-hint {
  font-size: 11px;
  color: rgba(9, 109, 217, 0.55);
  pointer-events: none;
}

.state-running {
  border-color: #1677ff !important;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.28) !important;
}

.state-done {
  border-color: #52c41a !important;
  box-shadow: 0 0 0 3px rgba(82, 196, 26, 0.28) !important;
}

.state-error {
  border-color: #ff4d4f !important;
  box-shadow: 0 0 0 3px rgba(255, 77, 79, 0.28) !important;
}

.state-precheck-error {
  border-color: #ff4d4f !important;
  box-shadow: 0 0 0 3px rgba(255, 77, 79, 0.4) !important;
}

.state-disabled {
  opacity: 0.45;
}
</style>
