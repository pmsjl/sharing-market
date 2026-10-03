<template>
  <article class="journal-entry">
    <div
      class="entry-main"
      :class="{ 'with-image': preview.thumbnail && !imageFailed }"
    >
      <div class="entry-copy">
        <h2>
          <router-link
            v-if="post.id && linkable"
            :to="{ name: 'PostDetail', params: { id: post.id } }"
            >{{ post.title || "未命名攻略" }}</router-link
          ><span v-else>{{ post.title || "未命名攻略" }}</span>
        </h2>
        <p class="entry-summary">
          {{ preview.summary || "打开文章，看看同学分享了什么。" }}
        </p>
      </div>
      <img
        v-if="preview.thumbnail && !imageFailed"
        class="entry-image"
        :src="preview.thumbnail"
        :alt="preview.imageAlt || '文章配图'"
        loading="lazy"
        @error="imageFailed = true"
      />
    </div>
    <div class="entry-byline">
      <el-avatar :src="post.user?.userAvatar" :size="22">{{
        (post.user?.userName || "同学").slice(0, 1)
      }}</el-avatar
      ><span>{{ post.user?.userName || "同学" }}</span
      ><time>{{ post.createTime?.slice(0, 10) || "发布时间未记录" }}</time>
    </div>
    <footer class="entry-footer">
      <div class="entry-topics">
        <span v-for="tag in post.tagList || []" :key="tag">#{{ tag }}</span>
      </div>
      <div class="entry-stats">
        <span
          :class="{ active: post.hasThumb }"
          :aria-label="`点赞 ${post.thumbNum || 0}`"
          ><svg viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M7 10v11H3V10h4Zm0 0 5-7c1-1 3 0 2 3l-1 4h6a2 2 0 0 1 2 2l-2 7a2 2 0 0 1-2 2H7"
            /></svg
          >{{ post.thumbNum || 0 }}</span
        ><span
          :class="{ active: post.hasFavour }"
          :aria-label="`收藏 ${post.favourNum || 0}`"
          ><el-icon><Star /></el-icon>{{ post.favourNum || 0 }}</span
        >
      </div>
    </footer>
    <div v-if="$slots.actions" class="entry-actions">
      <slot name="actions" />
    </div>
  </article>
</template>
<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { Star } from "@element-plus/icons-vue";
import { buildPostPreview } from "@/utils/postPreview";
const props = withDefaults(
  defineProps<{ post: API.PostVO; linkable?: boolean }>(),
  { linkable: true }
);
const preview = computed(() => buildPostPreview(props.post.content));
const imageFailed = ref(false);
watch(
  () => preview.value.thumbnail,
  () => {
    imageFailed.value = false;
  }
);
</script>
<style scoped lang="scss">
.journal-entry {
  padding: 28px 0;
  border-bottom: 1px solid var(--market-line);
  min-width: 0;
}
.entry-main {
  display: grid;
  gap: 24px;
  &.with-image {
    grid-template-columns: minmax(0, 1fr) 180px;
  }
}
.entry-copy {
  min-width: 0;
}
h2 {
  margin: 0 0 12px;
  font-size: 24px;
  font-weight: 650;
  line-height: 1.45;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  a {
    color: var(--market-ink);
    text-decoration: none;
    &:hover {
      color: var(--market-primary);
    }
    &:focus-visible {
      outline: 2px solid var(--market-primary);
      outline-offset: -2px;
    }
  }
}
.entry-summary {
  color: var(--market-muted);
  font-size: 15px;
  line-height: 1.85;
  margin: 0;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.entry-image {
  width: 180px;
  height: 120px;
  object-fit: cover;
  border-radius: 6px;
}
.entry-byline {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
  font-size: 13px;
  color: var(--market-muted);
  time {
    margin-left: 6px;
  }
}
.entry-footer {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 16px;
  margin-top: 14px;
  font-size: 13px;
  color: var(--market-muted);
}
.entry-topics {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  min-width: 0;
  overflow-wrap: anywhere;
}
.entry-stats {
  display: flex;
  gap: 16px;
  flex-shrink: 0;
  span {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }
  .active {
    color: var(--market-primary);
  }
  svg {
    width: 14px;
    height: 14px;
    fill: none;
    stroke: currentColor;
    stroke-width: 1.5;
  }
}
.entry-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}
@media (max-width: 600px) {
  .journal-entry {
    padding: 22px 0;
  }
  .entry-main {
    gap: 14px;
    &.with-image {
      grid-template-columns: minmax(0, 1fr) 96px;
    }
  }
  .entry-image {
    width: 96px;
    height: 72px;
  }
  h2 {
    font-size: 22px;
    margin-bottom: 8px;
  }
  .entry-summary {
    font-size: 15px;
    -webkit-line-clamp: 3;
  }
  .entry-footer {
    flex-wrap: wrap;
    gap: 10px;
  }
}
</style>
