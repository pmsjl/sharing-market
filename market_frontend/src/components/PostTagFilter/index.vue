<template>
  <div class="post-tag-filter">
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
      {{ mode === "any" ? "至少包含一个标签" : "同时包含所有标签" }}，最多 5 个
    </span>
  </div>
</template>

<script setup lang="ts">
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
