<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="min(460px, calc(100vw - 32px))"
    class="market-share-dialog"
    append-to-body
    align-center
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="share-content">
      <label class="share-label" :for="inputId">复制链接发给同学</label>
      <div class="share-link-row">
        <input :id="inputId" :value="url" readonly @focus="selectLink" />
        <el-button type="primary" @click="copyLink">复制链接</el-button>
      </div>
      <div class="share-qr">
        <QRCodeVue3
          :value="url"
          :width="384"
          :height="384"
          :margin="24"
          imgclass="market-share-qr-image"
          :dotsOptions="{ color: '#202b3d', type: 'square' }"
          :cornersSquareOptions="{ color: '#202b3d', type: 'square' }"
          :cornersDotOptions="{ color: '#202b3d', type: 'square' }"
          :backgroundOptions="{ color: '#ffffff' }"
        />
        <p>扫描二维码，打开{{ subject }}</p>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { getCurrentInstance } from "vue";
import { ElMessage } from "element-plus";
import QRCodeVue3 from "qrcode-vue3";
import useClipboard from "vue-clipboard3";
const props = defineProps<{
  modelValue: boolean;
  title: string;
  subject: string;
  url: string;
}>();
defineEmits<{ (event: "update:modelValue", value: boolean): void }>();
const inputId = `share-link-${getCurrentInstance()?.uid}`;
const selectLink = (event: FocusEvent) =>
  (event.target as HTMLInputElement).select();
const { toClipboard } = useClipboard();
const copyLink = async () => {
  try {
    await toClipboard(props.url);
    ElMessage.success("链接已复制");
  } catch {
    ElMessage.error("复制失败，可以选中链接手动复制");
  }
};
</script>

<style lang="scss">
/* Teleported to body so transformed/scrolling page containers cannot clip it. */
.el-dialog.market-share-dialog {
  width: min(460px, calc(100vw - 32px)) !important;
  max-height: calc(100vh - 32px);
  max-height: calc(100dvh - 32px);
  margin: auto !important;
  align-self: center;
  padding: 24px;
  overflow-y: auto;
  border-radius: 14px;
  font-family: var(--market-font-body);
  .el-dialog__title {
    font-family: inherit;
    font-size: 20px;
  }
  .el-dialog__body {
    min-width: 0;
    padding-top: 20px;
  }
  .share-content {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 14px;
    min-width: 0;
  }
  .share-label {
    color: var(--market-muted);
    font-size: 13px;
  }
  .share-link-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: 12px;
    input {
      box-sizing: border-box;
      min-width: 0;
      width: 100%;
      height: 44px;
      padding: 0 12px;
      border: 1px solid var(--market-line);
      border-radius: 8px;
      color: var(--market-ink);
      background: var(--market-surface);
      font: inherit;
      font-size: 13px;
      text-overflow: ellipsis;
      &:focus-visible {
        outline: 2px solid var(--market-primary);
        outline-offset: 2px;
      }
    }
    .el-button {
      min-height: 44px;
      margin: 0;
      padding: 0 16px;
    }
  }
  .market-share-qr-image {
    display: block;
    width: 192px;
    height: 192px;
  }
  .share-qr {
    display: grid;
    justify-items: center;
    gap: 12px;
    padding-top: 10px;
    p {
      margin: 0;
      color: var(--market-muted);
      font-size: 12px;
    }
  }
}
@media (max-width: 480px) {
  .el-dialog.market-share-dialog {
    padding: 20px;
  }
}
</style>
