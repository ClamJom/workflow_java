<script setup>
import { ref, computed, watch, nextTick } from 'vue';
import { Form, Textarea, AutoComplete, Tooltip } from 'ant-design-vue';

const props = defineProps({
  config: {
    type: Object,
    required: true
  },
  modelValue: {
    type: String,
    default: ''
  },
  pool: {
    type: Array,
    default: () => []
  },
  requestPoolRefresh: {
    type: Function,
    default: null
  }
});

const emit = defineEmits(['update:modelValue']);

const inputValue = ref('');
const textareaRef = ref(null);

const cacheBefore = ref('');
const cacheAfter = ref('');

const filteredOptions = computed(() => {
  if (!props.pool || props.pool.length === 0) return [];
  return props.pool.map(item => ({
    value: `{{${item.name}}}`,
    name: item.name,
    desc: item.des || item.type || ''
  }));
});

const visible = ref(false);

/** 与 StringConfig 一致：从 a-textarea 取原生 textarea，避免 a-input 的 ref 仅暴露 API 而无 $el 导致取不到 DOM */
function getTextareaEl() {
  const root = textareaRef.value;
  if (!root) return null;
  return root.resizableTextArea?.textArea || root.$el?.querySelector?.('textarea') || null;
}

watch(() => props.modelValue, (v) => {
  const s = v ?? '';
  if (s !== inputValue.value) {
    inputValue.value = s;
  }
}, { immediate: true });

watch(inputValue, (val) => {
  emit('update:modelValue', val);
});

async function onTextareaKeydown(e) {
  if (e.key !== '/') return;
  if (props.requestPoolRefresh) {
    try {
      await props.requestPoolRefresh();
    } catch {
      /* ignore */
    }
  }
  await nextTick();
  if (filteredOptions.value.length === 0) return;
  nextTick(() => {
    const ta = getTextareaEl();
    if (!ta) return;
    const val = ta.value;
    const pos = ta.selectionStart;
    if (pos >= 1 && val.charAt(pos - 1) === '/') {
      cacheBefore.value = val.slice(0, pos - 1);
      cacheAfter.value = val.slice(pos);
      visible.value = true;
    }
  });
}

function onDropdownVisibleChange(open) {
  if (!open) {
    visible.value = false;
  }
}

function onSelect(value) {
  inputValue.value = cacheBefore.value + value + cacheAfter.value;
  cacheBefore.value = '';
  cacheAfter.value = '';
  visible.value = false;
}
</script>

<template>
  <div class="number-config">
    <Form layout="vertical">
      <Form.Item :label="config.des || config.name">
        <Tooltip :title="`数字或变量；最小: ${config.min ?? '无'}, 最大: ${config.max ?? '无'}。输入 / 可从变量池选择`">
          <AutoComplete
            v-model:value="inputValue"
            :options="filteredOptions"
            :open="visible"
            :filter-option="false"
            placeholder="数字或输入 / 插入变量"
            style="width: 100%"
            @select="onSelect"
            @dropdown-visible-change="onDropdownVisibleChange"
          >
            <Textarea
              ref="textareaRef"
              :rows="1"
              class="number-config-textarea"
              @keydown="onTextareaKeydown"
            />
          </AutoComplete>
        </Tooltip>
      </Form.Item>
    </Form>
  </div>
</template>

<style scoped>
.number-config {
  width: 100%;
}

/* 单行外观，与原先 Input 接近 */
.number-config-textarea {
  resize: none;
  min-height: 32px;
  line-height: 1.5715;
}

:deep(.ant-form-item) {
  margin-bottom: 0;
}

:deep(.ant-form-item-label) {
  padding-bottom: 4px;
}

:deep(.ant-form-item-label > label) {
  font-size: 12px;
  color: #999;
}
</style>
