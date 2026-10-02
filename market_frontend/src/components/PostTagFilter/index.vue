<template>
  <div class="topic-filter">
    <div class="filter-summary">
      <button
        type="button"
        :aria-expanded="expanded"
        :aria-controls="`${inputId}-panel`"
        @click="expanded = !expanded"
      >
        筛选<span aria-hidden="true">{{
          expanded ? " −" : " ＋"
        }}</span></button
      ><span v-if="modelValue.length" class="filter-active"
        >{{ mode === "any" ? "任一话题" : "全部话题" }}：{{
          modelValue.map((tag) => "#" + tag).join("、")
        }}</span
      ><button v-if="modelValue.length" type="button" @click="updateTags([])">
        清除
      </button>
    </div>
    <div v-show="expanded" :id="`${inputId}-panel`" class="post-tag-filter">
      <label :for="inputId">标签筛选</label>
      <el-input-tag
        :id="inputId"
        :model-value="modelValue"
        :max="5"
        clearable
        :aria-label="
          mode === 'any'
            ? '标签筛选，至少包含一个标签'
            : '标签筛选，同时包含所有标签'
        "
        placeholder="输入标签后按 Enter"
        @update:model-value="updateTags"
      />
      <el-select
        :model-value="mode"
        class="match-mode"
        aria-label="标签匹配方式"
        @change="updateMode"
      >
        <el-option label="全部匹配" value="all" />
        <el-option label="任一匹配" value="any" />
      </el-select>
      <span class="filter-hint">
        {{ mode === "any" ? "至少包含一个标签" : "同时包含所有标签" }}，最多 5
        个
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
const expanded = ref(false);
import { ElInputTag, ElSelect, ElOption } from "element-plus";

const props = defineProps<{
  modelValue: string[];
  inputId: string;
  mode: "all" | "any";
}>();
const emit = defineEmits<{
  (event: "update:modelValue", tags: string[]): void;
  (event: "change"): void;
  (event: "update:mode", mode: "all" | "any"): void;
}>();

const updateTags = (value?: string[]) => {
  // 清空时 Element Plus 会传 undefined；去重后再提交筛选。
  const tags = [
    ...new Set((value || []).map((tag) => tag.trim()).filter(Boolean))
  ];
  if (
    tags.length === props.modelValue.length &&
    tags.every((tag, index) => tag === props.modelValue[index])
  ) {
    return;
  }
  emit("update:modelValue", tags);
  emit("change");
};

const updateMode = (value: "all" | "any") => {
  emit("update:mode", value);
  emit("change");
};
</script>

<style scoped lang="scss">
.topic-filter {
  border-bottom: 1px solid var(--market-line);
  padding: 10px 0 12px;
}
.filter-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--market-muted);
  font-size: 12px;
  button {
    flex-shrink: 0;
    min-height: 36px;
    padding: 0 4px;
    border: 0;
    background: transparent;
    color: var(--market-primary);
    font: inherit;
    cursor: pointer;
    &:focus-visible {
      outline: 2px solid var(--market-primary);
    }
  }
}
.filter-active {
  min-width: 0;
  overflow-wrap: anywhere;
}

.post-tag-filter {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 12px;
  margin-top: 12px;
  min-width: 0;

  label {
    flex: none;
    color: var(--market-ink);
    font-weight: 700;
  }

  .el-input-tag {
    flex: 1 1 240px;
    min-width: 0;
    max-width: 420px;
  }

  .match-mode {
    width: 130px;
    flex: none;
  }

  .filter-hint {
    color: var(--market-muted);
    font-size: 12px;
  }
}

@media (max-width: 760px) {
  .post-tag-filter {
    .el-input-tag {
      flex-basis: 100%;
      max-width: none;
    }

    .filter-hint {
      width: 100%;
    }
  }
}
</style>
