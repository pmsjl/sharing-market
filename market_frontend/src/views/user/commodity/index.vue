<template>
  <div class="market-page browse-market">
    <header class="browse-heading">
      <div>
        <span class="browse-kicker">好物循环</span>
        <h1>发现你的<span>下一件好物</span></h1>
      </div>
      <el-button type="primary" :icon="Plus" @click="publish"
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { listCommodityVoByPageUsingPost } from "@/api/commodityController";
import { listCommodityTypeVoByPageUsingPost } from "@/api/commodityTypeController";
import CommodityList from "@/components/CommodityList/index.vue";
import { ElMessage } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import { Search, Operation, Plus } from "@element-plus/icons-vue";

import { queryText, queryPage } from "@/utils/marketNavigation";
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
const syncQuery = () => {
  const query = {
    q: queryParams.value.commodityName.trim() || undefined,
    description: queryParams.value.commodityDescription || undefined,
    degree: queryParams.value.degree || undefined,
    inventory: queryParams.value.commodityInventory || undefined,
    category: queryParams.value.commodityTypeId || undefined,
    sort: sortMode.value,
    page: String(currentPage.value)
  };
  if (router.resolve({ path: route.path, query }).fullPath === route.fullPath)
    void getCommodityList();
  else void router.push({ query });
};
const searchCommodities = () => {
  currentPage.value = 1;
  syncQuery();
};
const publish = () =>
  router.push({ path: "/user/publish", query: { returnTo: route.fullPath } });
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
  syncQuery();
};

onMounted(() => {
  void getCommodityTypeList();
});
onUnmounted(() => {
  requestVersion += 1;
});
watch(
  () => route.query,
  (query) => {
    if (query.publish === "1") {
      const returnTo = router.resolve({
        path: "/user/commodity",
        query: { ...query, publish: undefined }
      }).fullPath;
      void router.replace({ path: "/user/publish", query: { returnTo } });
      return;
    }
    queryParams.value = {
      commodityName: queryText(query.q),
      commodityDescription: queryText(query.description),
      degree: queryText(query.degree),
      commodityInventory: queryText(query.inventory),
      commodityTypeId: queryText(query.category)
    };
    currentPage.value = queryPage(query.page);
    sortMode.value = ["priceAsc", "priceDesc"].includes(queryText(query.sort))
      ? queryText(query.sort)
      : "latest";
    advancedFiltersOpen.value = Boolean(advancedFilterCount.value);
    void getCommodityList();
  },
  { immediate: true }
);
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
    border-radius: var(--market-action-radius);
    padding: 0 18px;
    box-shadow: 0 0 0 1px var(--market-line) inset;
  }
  .el-button {
    min-height: 44px;
    padding-inline: 24px;
    border-radius: var(--market-action-radius);
  }
}
.more-filters {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 44px;
  padding: 0 14px;
  border: 0;
  border-radius: var(--market-action-radius);
  background: transparent;
  cursor: pointer;
  font-size: 14px;
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
  gap: 22px;
  overflow-x: auto;
  padding: 16px 0 0;
  border-bottom: 1px solid var(--market-line);
  scrollbar-width: thin;
  button {
    flex-shrink: 0;
    min-height: 46px;
    padding: 0 2px;
    border: 0;
    border-bottom: 2px solid transparent;
    border-radius: 0;
    color: var(--market-muted);
    background: transparent;
    font-size: 14px;
    cursor: pointer;
    transition: color 160ms;
    &:hover {
      color: var(--market-primary);
    }
    &.selected {
      color: var(--market-primary);
      border-bottom-color: currentColor;
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
    padding: 10px 0 0;
    gap: 18px;
    button {
      min-height: 38px;
      padding-inline: 2px;
      font-size: 14px;
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
}
</style>
