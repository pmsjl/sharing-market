<template>
  <div class="market-page browse-market">
    <header class="browse-heading">
      <div>
        <span class="browse-kicker">好物循环</span>
        <h1>发现你的<span>下一件好物</span></h1>
      </div>
      <el-button
        type="primary"
        :icon="Plus"
        round
        @click="addDialogVisible = true"
        >发布闲置</el-button
      >
    </header>
    <section class="browse-filters" aria-label="查找商品">
      <el-form @submit.prevent="searchCommodities">
        <div class="browse-search-row">
          <el-input
            v-model="queryParams.commodityName"
            :prefix-icon="Search"
            aria-label="搜索商品名称"
            placeholder="搜索教材、数码，或任何你需要的好物"
            clearable
            @clear="searchCommodities"
          />
          <el-button type="primary" native-type="submit">搜索</el-button>
          <button
            type="button"
            class="more-filters"
            :aria-expanded="advancedFiltersOpen"
            aria-controls="commodity-advanced-filters"
            @click="advancedFiltersOpen = !advancedFiltersOpen"
          >
            <el-icon><Operation /></el-icon>筛选<span
              v-if="advancedFilterCount"
              class="filter-count"
              >{{ advancedFilterCount }}</span
            >
          </button>
        </div>
        <div
          v-show="advancedFiltersOpen"
          id="commodity-advanced-filters"
          class="browse-advanced"
        >
          <el-form-item label="简介关键词"
            ><el-input
              v-model="queryParams.commodityDescription"
              placeholder="品牌或用途"
              clearable
          /></el-form-item>
          <el-form-item label="新旧程度"
            ><el-input
              v-model="queryParams.degree"
              placeholder="例如：九成新"
              clearable
          /></el-form-item>
          <el-form-item label="库存数量"
            ><el-input
              v-model="queryParams.commodityInventory"
              placeholder="按准确数量筛选"
              clearable
          /></el-form-item>
          <div class="advanced-actions">
            <el-button native-type="submit">应用筛选</el-button
            ><el-button text @click="resetQuery">重置全部</el-button>
          </div>
        </div>
      </el-form>
      <div class="category-chips" role="group" aria-label="商品分类">
        <button
          type="button"
          :class="{ selected: !queryParams.commodityTypeId }"
          :aria-pressed="!queryParams.commodityTypeId"
          @click="selectCategory()"
        >
          全部好物
        </button>
        <button
          v-for="type in commodityTypeList"
          :key="type.id"
          type="button"
          :class="{ selected: queryParams.commodityTypeId === String(type.id) }"
          :aria-pressed="queryParams.commodityTypeId === String(type.id)"
          @click="selectCategory(String(type.id))"
        >
          {{ type.typeName }}
        </button>
      </div>
    </section>
    <div class="browse-results-heading">
      <span role="status">{{
        loading
          ? "正在寻找好物…"
          : loadFailed
          ? "暂时无法加载"
          : `找到 ${total} 件好物`
      }}</span>
      <el-select
        v-model="sortMode"
        aria-label="商品排序"
        class="browse-sort"
        @change="searchCommodities"
        ><el-option label="最新上架" value="latest" /><el-option
          label="价格从低到高"
          value="priceAsc" /><el-option label="价格从高到低" value="priceDesc"
      /></el-select>
    </div>
    <div
      v-if="loading"
      class="browse-skeleton"
      aria-label="正在加载商品"
      :aria-busy="true"
    >
      <el-skeleton v-for="n in 4" :key="n" animated
        ><template #template
          ><el-skeleton-item
            variant="image"
            class="skeleton-cover" /><el-skeleton-item
            variant="h3" /><el-skeleton-item variant="text" /></template
      ></el-skeleton>
    </div>
    <div v-else-if="loadFailed" class="browse-error" role="status">
      <h2>好物暂时没加载出来</h2>
      <p>稍后再试试，已填写的筛选条件会保留。</p>
      <el-button @click="getCommodityList">重新加载</el-button>
    </div>
    <CommodityList v-else :commodity-list="commodityList"
      ><template #empty-action
        ><el-button @click="resetQuery">清除筛选</el-button></template
      ></CommodityList
    >
    <div v-if="!loading && !loadFailed && total > 0" class="browse-pagination">
      <el-pagination
        background
        layout="prev, pager, next"
        :pager-count="5"
        :total="total"
        :page-size="pageSize"
        :current-page="currentPage"
        @current-change="handlePageChange"
      />
    </div>
    <el-dialog
      title="发布商品"
      v-model="addDialogVisible"
      width="560px"
      @close="resetAddForm"
    >
      <el-form :model="addForm" ref="addFormRef" label-width="100px">
        <el-form-item label="商品名称" prop="commodityName">
          <el-input
            v-model="addForm.commodityName"
            placeholder="请输入商品名称"
          />
        </el-form-item>
        <el-form-item label="商品简介" prop="commodityDescription">
          <el-input
            type="textarea"
            v-model="addForm.commodityDescription"
            placeholder="写清品牌、成色、适用场景"
            :rows="4"
          />
        </el-form-item>
        <el-form-item label="商品封面" prop="commodityAvatar">
          <div class="upload-row">
            <el-input
              v-model="addForm.commodityAvatar"
              placeholder="请输入图片 URL 或上传本地封面"
            />
            <el-upload
              :http-request="handleCommodityAvatarUpload"
              :show-file-list="false"
              accept="image/*"
            >
              <el-button type="primary">上传封面</el-button>
            </el-upload>
          </div>
          <el-image
            v-if="addForm.commodityAvatar"
            :src="addForm.commodityAvatar"
            class="preview-image"
            :preview-src-list="[addForm.commodityAvatar]"
          />
        </el-form-item>
        <el-form-item label="新旧程度" prop="degree">
          <el-input v-model="addForm.degree" placeholder="例如：九成新" />
        </el-form-item>
        <el-form-item label="商品分类" prop="commodityTypeId">
          <el-select
            v-model="addForm.commodityTypeId"
            placeholder="请选择商品分类"
          >
            <el-option
              v-for="type in commodityTypeList"
              :key="type.id"
              :label="type.typeName"
              :value="type.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="价格（校园币）" prop="price">
          <el-input v-model="addForm.price" placeholder="请输入价格" />
        </el-form-item>
        <el-form-item label="商品库存" prop="commodityInventory">
          <el-input-number
            v-model="addForm.commodityInventory"
            :min="1"
            :step="1"
            controls-position="right"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAddCommodity">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import {
  addCommodityUsingPost,
  listCommodityVoByPageUsingPost
} from "@/api/commodityController";
import { uploadFileUsingPost } from "@/api/fileController";
import { listCommodityTypeVoByPageUsingPost } from "@/api/commodityTypeController";
import CommodityList from "@/components/CommodityList/index.vue";
import { ElMessage } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import { Search, Operation, Plus } from "@element-plus/icons-vue";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const loadFailed = ref(false);
let requestVersion = 0;
const commodityList = ref<API.CommodityVO[]>([]);
const total = ref(0);
const pageSize = ref(8);
const currentPage = ref(1);
const commodityTypeList = ref<API.CommodityTypeVO[]>([]);

const advancedFiltersOpen = ref(false);
const sortMode = ref("latest");
const advancedFilterCount = computed(
  () =>
    [
      queryParams.value.commodityDescription,
      queryParams.value.degree,
      queryParams.value.commodityInventory
    ].filter(Boolean).length
);
const searchCommodities = () => {
  currentPage.value = 1;
  const q = queryParams.value.commodityName.trim();
  queryParams.value.commodityName = q;
  if (q !== (route.query.q || "")) {
    void router.replace({ query: { ...route.query, q: q || undefined } });
  } else {
    void getCommodityList();
  }
};
const selectCategory = (id = "") => {
  queryParams.value.commodityTypeId = id;
  searchCommodities();
};

const queryParams = ref({
  commodityName: typeof route.query.q === "string" ? route.query.q : "",
  commodityDescription: "",
  degree: "",
  commodityInventory: "",
  commodityTypeId: ""
});

const getCommodityList = async () => {
  const version = ++requestVersion;
  loading.value = true;
  loadFailed.value = false;
  try {
    const res = await listCommodityVoByPageUsingPost({
      current: currentPage.value,
      pageSize: pageSize.value,
      commodityName: queryParams.value.commodityName,
      commodityDescription: queryParams.value.commodityDescription,
      degree: queryParams.value.degree,
      commodityInventory: queryParams.value.commodityInventory,
      commodityTypeId: queryParams.value.commodityTypeId,
      sortField: sortMode.value === "latest" ? "createTime" : "price",
      sortOrder: sortMode.value === "priceAsc" ? "asc" : "desc",
      isListed: 1
    });
    if (version !== requestVersion) return;
    if (res.code !== 200) throw new Error("商品加载失败");
    commodityList.value = res.data.records || [];
    total.value = Number(res.data.total) || 0;
  } catch {
    if (version === requestVersion) loadFailed.value = true;
  } finally {
    if (version === requestVersion) loading.value = false;
  }
};

const getCommodityTypeList = async () => {
  try {
    const categories = [];
    let current = 1;
    let hasMore = true;
    while (hasMore) {
      const res = await listCommodityTypeVoByPageUsingPost({
        pageSize: 100,
        current,
        sortField: "id",
        sortOrder: "asc"
      });
      if (res.code !== 200) {
        ElMessage.error("获取商品分类列表失败");
        return;
      }
      const records = res.data.records;
      categories.push(...records);
      hasMore = records.length > 0 && categories.length < res.data.total;
      current += 1;
    }
    commodityTypeList.value = categories;
  } catch {
    ElMessage.error("获取商品分类列表失败");
  }
};

const resetQuery = () => {
  currentPage.value = 1;
  sortMode.value = "latest";
  queryParams.value = {
    commodityName: "",
    commodityDescription: "",
    degree: "",
    commodityInventory: "",
    commodityTypeId: ""
  };
  searchCommodities();
};

const handlePageChange = (page: number) => {
  currentPage.value = page;
  getCommodityList();
};

onMounted(() => {
  void getCommodityList();
  void getCommodityTypeList();
});
onUnmounted(() => {
  requestVersion += 1;
});
watch(
  () => route.query.q,
  (q) => {
    queryParams.value.commodityName = typeof q === "string" ? q : "";
    currentPage.value = 1;
    void getCommodityList();
  }
);

const addDialogVisible = ref(false);
watch(
  () => route.query.publish,
  (publish) => {
    if (publish !== "1") return;
    addDialogVisible.value = true;
    void router.replace({ query: { ...route.query, publish: undefined } });
  },
  { immediate: true }
);

const addForm = ref({
  commodityName: "",
  commodityDescription: "",
  degree: "",
  commodityTypeId: "",
  price: 0,
  commodityAvatar: "",
  commodityInventory: 1
});

const handleAddCommodity = async () => {
  try {
    const res = await addCommodityUsingPost(addForm.value);
    if (res.code === 200) {
      ElMessage.success("发布成功");
      addDialogVisible.value = false;
      resetAddForm();
      await getCommodityList();
    } else {
      ElMessage.error("发布失败");
    }
  } catch (error) {
    ElMessage.error("发布失败");
  }
};

const handleCommodityAvatarUpload = async (options: any) => {
  try {
    const res = await uploadFileUsingPost(
      { biz: "commodity_avatar" },
      {},
      options.file
    );
    if (res.code !== 200) {
      return ElMessage.error("上传封面失败");
    }
    addForm.value.commodityAvatar = res.data || "";
    ElMessage.success("上传封面成功");
  } catch (error) {
    ElMessage.error("上传封面失败");
  }
};

const resetAddForm = () => {
  addForm.value = {
    commodityName: "",
    commodityDescription: "",
    degree: "",
    commodityTypeId: "",
    price: 0,
    commodityAvatar: "",
    commodityInventory: 1
  };
};
</script>

<style scoped lang="scss">
.browse-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 4px 0 26px;
}
.browse-kicker {
  display: block;
  margin-bottom: 8px;
  color: var(--market-muted);
  font-size: 11px;
  letter-spacing: 2px;
}
h1 {
  font-size: 32px;
  font-weight: 750;
  line-height: 1.4;
  letter-spacing: -1.5px;
  span {
    position: relative;
    z-index: 0;
    white-space: nowrap;
  }
  span::after {
    content: "";
    position: absolute;
    left: 1px;
    right: 0;
    bottom: 2px;
    height: 10px;
    background: var(--market-sticker-yellow, #ffe58b);
    z-index: -1;
    transform: rotate(-1deg);
  }
}
.browse-search-row {
  display: flex;
  gap: 10px;
  align-items: center;
  max-width: 800px;
  .el-input {
    flex: 1;
  }
  :deep(.el-input__wrapper) {
    min-height: 46px;
    border-radius: 999px;
    padding: 0 18px;
    box-shadow: 0 0 0 1px var(--market-line) inset;
  }
  .el-button {
    min-height: 44px;
    padding-inline: 24px;
    border-radius: 999px;
  }
}
.more-filters {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid var(--market-line);
  border-radius: 999px;
  background: var(--market-surface);
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
  .el-icon {
    font-size: 17px;
  }
}
.filter-count {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--market-primary);
  color: var(--market-on-primary);
  font-size: 10px;
}
.browse-advanced {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  padding: 20px;
  margin-top: 16px;
  border-radius: 14px;
  background: var(--market-surface-soft);
  .el-form-item {
    display: block;
    margin: 0;
  }
}
.advanced-actions {
  grid-column: 1 / -1;
}
.category-chips {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 20px 0 18px;
  border-bottom: 1px solid var(--market-line);
  scrollbar-width: thin;
  button {
    flex-shrink: 0;
    min-height: 38px;
    padding: 0 18px;
    border: 1px solid transparent;
    border-radius: 999px;
    color: var(--market-muted);
    background: transparent;
    font-size: 12px;
    cursor: pointer;
    transition: background 180ms, color 180ms;
    &:hover {
      background: var(--market-surface-soft);
      color: var(--market-ink);
    }
    &.selected {
      background: var(--market-ink);
      color: var(--market-canvas);
    }
  }
}
.browse-results-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin: 18px 0;
  > span {
    font-size: 12px;
    color: var(--market-muted);
  }
}
.browse-sort {
  width: 150px;
  :deep(.el-select__wrapper) {
    background: transparent;
    box-shadow: none;
    font-size: 12px;
  }
}
.browse-skeleton {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 22px;
  .skeleton-cover {
    width: 100%;
    height: auto;
    aspect-ratio: 1.15;
    border-radius: 14px;
    margin-bottom: 12px;
  }
}
.browse-error {
  display: grid;
  justify-items: center;
  gap: 14px;
  padding: 48px 20px;
  text-align: center;
  background: var(--market-surface-soft);
  border-radius: 16px;
  h2 {
    font-size: 20px;
    font-weight: 650;
  }
  p {
    font-size: 13px;
    color: var(--market-muted);
  }
}
.browse-pagination {
  display: flex;
  justify-content: center;
  margin-top: 36px;
}
.upload-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  width: 100%;
}
.preview-image {
  width: 150px;
  height: 150px;
  margin-top: 12px;
  border-radius: 8px;
}
@media (max-width: 760px) {
  .browse-heading {
    margin: 0 0 18px;
    align-items: flex-end;
    gap: 8px;
    .el-button {
      padding-inline: 12px;
      font-size: 11px;
      min-height: 36px;
    }
  }
  .browse-kicker {
    font-size: 9px;
    margin-bottom: 6px;
    letter-spacing: 1px;
  }
  h1 {
    font-size: 25px;
    letter-spacing: -1px;
    span {
      display: block;
      width: fit-content;
    }
  }
  .browse-search-row {
    gap: 6px;
    .el-button {
      padding-inline: 15px;
      font-size: 12px;
    }
    :deep(.el-input__wrapper) {
      padding: 0 12px;
    }
    :deep(.el-input__inner) {
      font-size: 16px;
    }
  }
  .more-filters {
    padding-inline: 10px;
    gap: 4px;
  }
  .category-chips {
    padding: 14px 0 12px;
    gap: 4px;
    button {
      min-height: 38px;
      padding-inline: 14px;
      font-size: 11px;
    }
  }
  .browse-results-heading {
    margin: 12px 0;
  }
  .browse-advanced {
    grid-template-columns: 1fr;
  }
  .browse-skeleton {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }
  .upload-row {
    grid-template-columns: 1fr;
  }
}
</style>
