<script setup>
import {Handle, Position, useVueFlow} from '@vue-flow/core';
import {NodeResizer} from '@vue-flow/node-resizer';
import {computed, nextTick} from 'vue';
import '@vue-flow/node-resizer/dist/style.css';

const props = defineProps(['id', 'data']);

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
  <div class="loop-root">
    <NodeResizer
      class="loop-node-resizer"
      :min-width="280"
      :min-height="200"
      :max-width="2400"
      :max-height="1600"
      @resize-end="onResizeEnd"
    />
    <div class="loop-shell" :class="uiStateClass">
      <div class="loop-chrome">
        <Handle type="target" :position="Position.Left" id="target" class="loop-handle loop-handle-left" />
        <span class="loop-title">{{ data?.wnode?.name || '循环' }}</span>
        <Handle type="source" :position="Position.Right" id="source" class="loop-handle loop-handle-right" />
      </div>
      <div class="loop-drop-area" aria-hidden="true">
        <span class="loop-drop-hint">子图区域 · 可将外部节点拖入</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.loop-root {
  width: 100%;
  height: 100%;
  position: relative;
}

/* 隐藏 NodeResizer 边线/手柄（仍可拖拽边缘与角点调整大小） */
.loop-root :deep(.vue-flow__resize-control) {
  opacity: 0;
}
.loop-root :deep(.vue-flow__resize-control *) {
  opacity: 0 !important;
  stroke: transparent !important;
  fill: transparent !important;
}

.loop-shell {
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
  border: 2px solid #722ed1;
  background: rgba(250, 245, 255, 0.42);
  box-shadow: 0 2px 12px rgba(114, 46, 209, 0.12);
  transition: box-shadow 0.2s, border-color 0.2s;
  pointer-events: none;
}

.loop-chrome {
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 8px 28px;
  background: rgba(255, 255, 255, 0.88);
  border-bottom: 1px solid rgba(114, 46, 209, 0.2);
  border-radius: 10px 10px 0 0;
  min-height: 40px;
  pointer-events: auto;
}

.loop-title {
  font-size: 13px;
  font-weight: 600;
  color: #391085;
  pointer-events: none;
  text-align: center;
}

.loop-handle {
  position: absolute !important;
  top: 50% !important;
  transform: translateY(-50%) !important;
}

.loop-handle-left {
  left: -6px !important;
}

.loop-handle-right {
  right: -6px !important;
}

.loop-drop-area {
  flex: 1;
  min-height: 120px;
  margin: 10px;
  border-radius: 8px;
  border: 1px dashed rgba(114, 46, 209, 0.35);
  background: rgba(114, 46, 209, 0.06);
  pointer-events: none;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 8px;
}

.loop-drop-hint {
  font-size: 11px;
  color: rgba(57, 16, 133, 0.55);
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
