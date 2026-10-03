<template>
  <section class="editor-page publish-page editorial-surface">
    <router-link class="editor-back" :to="returnTo">← 返回商品列表</router-link>
    <header class="quiet-heading">
      <h1>发布一件好物</h1>
      <p>给闲置一个新去处，把喜欢传给下一位同学。</p>
    </header>
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :disabled="busy"
      label-position="top"
      @submit.prevent="submit"
    >
      <div class="publish-grid">
        <section class="publish-cover" aria-label="商品封面">
          <div class="cover-caption">
            <span>先从一张照片开始</span><small>单张封面</small>
          </div>
          <input
            ref="coverFileInput"
            type="file"
            accept="image/*"
            hidden
            :disabled="busy"
            @change="selectCover"
          />
          <button
            type="button"
            class="cover-trigger"
            :class="{ 'has-cover': form.commodityAvatar }"
            :disabled="busy"
            :aria-label="form.commodityAvatar ? '更换商品封面' : '上传商品封面'"
            :aria-busy="uploading"
            @click="coverFileInput?.click()"
          >
            <el-image
              v-if="form.commodityAvatar"
              class="cover-preview"
              :src="form.commodityAvatar"
              fit="contain"
              ><template #error
                ><span class="cover-failed"
                  ><el-icon><Picture /></el-icon>图片无法显示，点击更换</span
                ></template
              ></el-image
            >
            <span v-else class="cover-placeholder"
              ><span class="cover-add"
                ><el-icon><Plus /></el-icon></span
              ><strong>拍下好物的样子</strong
              ><span>点击上传一张照片</span></span
            >
            <span v-if="uploading" class="cover-progress" role="status"
              >正在上传…</span
            ><span v-else-if="form.commodityAvatar" class="cover-replace"
              ><el-icon><Refresh /></el-icon>更换封面</span
            >
          </button>
          <p v-if="uploadError" class="field-error" role="alert">
            {{ uploadError }}
          </p>
          <details class="image-link">
            <summary>使用图片链接</summary>
            <el-input
              v-model="form.commodityAvatar"
              aria-label="封面图片链接"
              placeholder="https://…"
              :disabled="busy"
            />
          </details>
          <p class="cover-tip">让书页、细节和使用痕迹，都被看见。</p>
        </section>
        <div class="publish-fields">
          <el-form-item label="商品名称" prop="commodityName" class="name-field"
            ><el-input
              v-model="form.commodityName"
              placeholder="这件好物，叫什么？"
          /></el-form-item>
          <el-form-item label="商品介绍" prop="commodityDescription"
            ><el-input
              v-model="form.commodityDescription"
              type="textarea"
              :rows="5"
              placeholder="用了多久？哪里保存得很好？有哪些小瑕疵？真实地说说它。"
          /></el-form-item>
          <div class="field-pair">
            <el-form-item label="成色" prop="degree"
              ><el-input
                v-model="form.degree"
                placeholder="例如：九成新" /></el-form-item
            ><el-form-item label="分类" prop="commodityTypeId"
              ><el-select
                v-model="form.commodityTypeId"
                placeholder="选择分类"
                :loading="categories.loading.value"
                ><el-option
                  v-for="item in categories.data.value"
                  :key="item.id"
                  :value="item.id"
                  :label="item.typeName"
              /></el-select>
              <p v-if="categories.error.value" class="field-error">
                分类加载失败
                <el-button link @click="categories.load">重试</el-button>
              </p></el-form-item
            >
          </div>
          <div class="pricing-fields">
            <el-form-item label="价格" prop="price" class="price-field"
              ><el-input
                v-model="form.price"
                inputmode="decimal"
                placeholder="0.00"
                ><template #suffix
                  ><span class="coin-unit">校园币</span></template
                ></el-input
              ></el-form-item
            ><el-form-item label="数量" prop="commodityInventory"
              ><el-input-number
                v-model="form.commodityInventory"
                :min="1"
                :step="1"
                step-strictly
                controls-position="right"
            /></el-form-item>
          </div>
        </div>
      </div>
      <p v-if="submitError" class="field-error" role="alert">
        {{ submitError }}
      </p>
      <footer class="editor-actions">
        <span class="editor-count">真实描述，让交易更安心。</span
        ><el-button
          type="primary"
          native-type="submit"
          :loading="saving"
          :disabled="uploading"
          >发布好物</el-button
        >
      </footer>
    </el-form>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, FormInstance, FormRules } from "element-plus";
import { Picture, Plus, Refresh } from "@element-plus/icons-vue";
import { addCommodityUsingPost } from "@/api/commodityController";
import { listCommodityTypeVoByPageUsingPost } from "@/api/commodityTypeController";
import { uploadFileUsingPost } from "@/api/fileController";
import { editorReturnPath } from "@/utils/marketNavigation";
import { useRemote, responseData } from "@/composables/useRemote";
import { useUnsavedChanges } from "@/composables/useUnsavedChanges";
const route = useRoute(),
  router = useRouter();
const returnTo = computed(() =>
  editorReturnPath(route.query.returnTo, "/user/commodity")
);
const form = reactive({
  commodityName: "",
  commodityDescription: "",
  commodityAvatar: "",
  degree: "",
  commodityTypeId: "",
  price: "",
  commodityInventory: 1
});
const original = ref(JSON.stringify(form));
const saving = ref(false),
  uploading = ref(false),
  submitError = ref(""),
  uploadError = ref("");
const busy = computed(() => saving.value || uploading.value);
useUnsavedChanges(
  computed(() => original.value !== JSON.stringify(form)),
  busy
);
const formRef = ref<FormInstance>();
const coverFileInput = ref<HTMLInputElement>();
async function selectCover(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file) await upload({ file });
  input.value = "";
}
const rules: FormRules = {
  commodityName: [
    {
      required: true,
      whitespace: true,
      message: "请填写商品名称",
      trigger: "blur"
    }
  ],
  commodityTypeId: [
    { required: true, message: "请选择商品分类", trigger: "change" }
  ],
  price: [
    {
      validator: (_rule, value, callback) =>
        Number.isFinite(Number(value)) && Number(value) > 0
          ? callback()
          : callback(new Error("价格必须大于 0")),
      trigger: "blur"
    }
  ],
  commodityInventory: [
    {
      validator: (_rule, value, callback) =>
        Number.isInteger(value) && value > 0
          ? callback()
          : callback(new Error("数量须为正整数")),
      trigger: "change"
    }
  ]
};
const categories = useRemote<API.CommodityTypeVO[]>([], async () => {
  const all: API.CommodityTypeVO[] = [];
  let current = 1;
  let total = Infinity;
  do {
    const result = responseData(
      await listCommodityTypeVoByPageUsingPost({
        current: current++,
        pageSize: 100,
        sortField: "id",
        sortOrder: "asc"
      })
    );
    all.push(...(result.records || []));
    total = Number(result.total || 0);
    if (!result.records?.length) break;
  } while (all.length < total);
  return all;
});
async function upload(options: any) {
  if (busy.value) return;
  uploading.value = true;
  uploadError.value = "";
  try {
    form.commodityAvatar = responseData(
      await uploadFileUsingPost({ biz: "commodity_avatar" }, {}, options.file)
    );
  } catch {
    uploadError.value = "上传失败，请重试。已填写内容会保留。";
  } finally {
    uploading.value = false;
  }
}
async function submit() {
  if (busy.value) return;
  saving.value = true;
  submitError.value = "";
  try {
    if (!(await formRef.value?.validate().catch(() => false))) return;
    responseData(
      await addCommodityUsingPost({ ...form, price: Number(form.price) })
    );
    original.value = JSON.stringify(form);
    saving.value = false;
    ElMessage.success("好物已发布");
    await router.replace(returnTo.value);
  } catch (error) {
    submitError.value =
      error instanceof Error ? error.message : "发布失败，请重试";
  } finally {
    saving.value = false;
  }
}
onMounted(() => void categories.load());
</script>
<style scoped lang="scss">
.publish-page {
  max-width: 1120px;
}
.publish-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.44fr) minmax(0, 0.56fr);
  gap: clamp(28px, 5vw, 64px);
  align-items: start;
  margin: 42px 0 28px;
}
.publish-cover,
.publish-fields {
  min-width: 0;
}
.cover-caption {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  margin-bottom: 14px;
  small {
    color: var(--market-muted);
    font-size: 12px;
  }
}
.cover-trigger {
  position: relative;
  display: block;
  width: 100%;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 16px;
  aspect-ratio: 4/3;
  overflow: hidden;
  background: var(--market-surface-soft);
  font: inherit;
  cursor: pointer;
  color: var(--market-ink);
  transition: background 160ms, border-color 160ms;
  &:hover:not(:disabled) {
    border-color: var(--market-primary);
    background: var(--market-primary-soft);
  }
  &:disabled {
    cursor: wait;
  }
}
.cover-preview {
  position: absolute;
  inset: 0;
  height: 100%;
  width: 100%;
}
.cover-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 20px;
  strong {
    font-size: 20px;
    font-weight: 600;
  }
  > span:last-child {
    font-size: 13px;
    color: var(--market-muted);
  }
}
.cover-add {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: var(--market-surface);
  color: var(--market-primary);
  font-size: 26px;
  margin-bottom: 6px;
}
.cover-failed {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  color: var(--market-muted);
  .el-icon {
    font-size: 32px;
  }
}
.cover-replace,
.cover-progress {
  position: absolute;
  left: 50%;
  bottom: 16px;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  padding: 9px 16px;
  border-radius: 24px;
  background: var(--market-surface);
  color: var(--market-ink);
  font-size: 13px;
  box-shadow: var(--market-shadow-soft);
}
.image-link {
  margin-top: 12px;
  summary {
    display: list-item;
    cursor: pointer;
    width: fit-content;
    color: var(--market-muted);
    font-size: 13px;
    padding: 12px 0;
  }
}
.cover-tip {
  margin: 22px 0 0;
  max-width: 30ch;
  color: var(--market-muted);
  font-size: 13px;
  line-height: 1.8;
}
.name-field :deep(.el-input__inner) {
  font-size: 22px;
  font-weight: 600;
}
.name-field :deep(.el-input__wrapper) {
  min-height: 54px;
}
.field-pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}
.pricing-fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 130px;
  gap: 24px;
  margin-top: 10px;
  padding-top: 24px;
  border-top: 1px solid var(--market-line);
}
.price-field :deep(.el-input__inner) {
  font-size: 26px;
  font-weight: 650;
}
.coin-unit {
  font-size: 13px;
  color: var(--market-muted);
}
.field-error {
  color: var(--el-color-danger);
  font-size: 13px;
  line-height: 1.7;
  margin: 12px 0;
}
@media (max-width: 760px) {
  .publish-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 30px;
    margin-top: 28px;
  }
  .cover-tip {
    display: none;
  }
  .field-pair {
    gap: 14px;
  }
  .pricing-fields {
    grid-template-columns: minmax(0, 1fr) 108px;
    gap: 16px;
  }
  .name-field :deep(.el-input__inner) {
    font-size: 20px;
  }
}
</style>
