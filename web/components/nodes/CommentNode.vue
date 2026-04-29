<script setup>
import {computed} from 'vue';

const props = defineProps(['id', 'data']);

const displayText = computed(() => {
  const t = props.data?.wnode?.name;
  return (t != null && String(t).trim() !== '') ? t : '注释';
});
</script>

<template>
  <div class="workflow-node comment-node">
    <div class="comment-body">{{ displayText }}</div>
  </div>
</template>

<style scoped>
.workflow-node {
  min-width: 140px;
  max-width: 280px;
  min-height: 44px;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.45;
  cursor: pointer;
  user-select: none;
  border: 1px dashed rgba(60, 120, 60, 0.45);
  box-sizing: border-box;
}

.comment-node {
  background: rgba(183, 235, 183, 0.42);
  color: #2d4a2d;
}

.comment-body {
  white-space: pre-wrap;
  word-break: break-word;
  pointer-events: none;
}
</style>
