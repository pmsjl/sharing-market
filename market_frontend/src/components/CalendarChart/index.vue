<template>
  <div class="calendar-chart-shell">
    <div class="calendar-heading">
      <div>
        <span>SHOPPING CALENDAR</span>
        <strong>{{ year }} 年购物印记</strong>
      </div>
      <small>颜色越深，成交越活跃</small>
    </div>
    <div ref="chartDom" class="calendar-chart"></div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import * as echarts from "echarts";

const props = defineProps<{
  data: { date: string; value: number }[];
  year: string;
}>();

const chartDom = ref<HTMLElement | null>(null);
let chartInstance: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;
let themeObserver: MutationObserver | null = null;
let renderTimer: number | null = null;

const getChartWidth = () => chartDom.value?.clientWidth || 0;

const renderChart = async () => {
  await nextTick();
  if (!chartDom.value) {
    return;
  }

  const chartWidth = getChartWidth();
  if (chartWidth < 300) {
    return;
  }

  if (!chartInstance) {
    chartInstance = echarts.init(chartDom.value);
  }

  const styles = getComputedStyle(chartDom.value);
  const color = (name: string) => styles.getPropertyValue(name).trim();
  const primary = color("--market-primary");
  const muted = color("--market-muted");
  const line = color("--market-line");
  const soft = color("--market-surface-soft");
  const today = new Intl.DateTimeFormat("en-CA").format(new Date());
  const eChartsData = props.data.map((item) => ({
    value: [item.date, item.value],
    itemStyle:
      item.date === today
        ? {
            borderColor: primary,
            borderWidth: 2
          }
        : undefined
  }));
  const maxValue = Math.max(1, ...props.data.map((item) => item.value || 0));
  const cellWidth = Math.max(
    12,
    Math.min(18, Math.floor((chartWidth - 130) / 54))
  );

  chartInstance.setOption(
    {
      tooltip: {
        borderWidth: 1,
        borderColor: line,
        backgroundColor: color("--market-surface"),
        textStyle: {
          color: color("--market-ink"),
          fontFamily: '"PingFang SC", "Microsoft YaHei", sans-serif'
        },
        extraCssText:
          "border-radius:8px;box-shadow:0 12px 26px rgba(35,49,63,.2);",
        formatter: (params: any) => {
          const date = params.data?.value?.[0] || "";
          const value = params.data?.value?.[1] || 0;
          return `<strong>${date}</strong><br/>成交印记：${value} 次`;
        }
      },
      visualMap: {
        show: false,
        min: 0,
        max: maxValue,
        inRange: {
          color: [soft, color("--market-primary-soft"), primary]
        }
      },
      calendar: {
        orient: "horizontal",
        range: props.year,
        cellSize: [cellWidth, 18],
        left: 80,
        right: 24,
        top: 74,
        bottom: 34,
        yearLabel: {
          show: true,
          position: "top",
          margin: 18,
          color: muted,
          fontSize: 18,
          fontWeight: 600
        },
        dayLabel: {
          firstDay: 1,
          nameMap: "ZH",
          margin: 10,
          color: muted
        },
        monthLabel: {
          nameMap: "ZH",
          margin: 12,
          color: muted
        },
        itemStyle: {
          borderColor: line,
          borderWidth: 1
        }
      },
      series: [
        {
          type: "heatmap",
          coordinateSystem: "calendar",
          data: eChartsData,
          itemStyle: {
            borderRadius: 8,
            opacity: 0.9
          },
          emphasis: {
            itemStyle: {
              borderColor: primary,
              borderWidth: 2,
              shadowBlur: 0
            }
          }
        }
      ]
    },
    true
  );

  chartInstance.resize({ width: chartWidth });
};

const scheduleRender = () => {
  if (renderTimer !== null) {
    window.cancelAnimationFrame(renderTimer);
  }
  renderTimer = window.requestAnimationFrame(() => {
    renderTimer = null;
    renderChart();
  });
};

onMounted(() => {
  themeObserver = new MutationObserver(scheduleRender);
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["class", "style", "data-theme"]
  });
  if (chartDom.value) {
    resizeObserver = new ResizeObserver(() => {
      scheduleRender();
      chartInstance?.resize();
    });
    resizeObserver.observe(chartDom.value);
  }
  scheduleRender();
});

onUnmounted(() => {
  if (renderTimer !== null) {
    window.cancelAnimationFrame(renderTimer);
  }
  themeObserver?.disconnect();
  resizeObserver?.disconnect();
  resizeObserver = null;
  chartInstance?.dispose();
  chartInstance = null;
});

watch(
  () => [props.data, props.year],
  () => {
    scheduleRender();
  },
  { deep: true }
);
</script>

<style scoped lang="scss">
.calendar-chart-shell {
  width: 100%;
  overflow-x: auto;
  padding: 20px;
  background: transparent;
}

.calendar-heading {
  position: sticky;
  left: 0;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  min-width: 620px;
  padding: 0 8px 12px;
  border-bottom: 1px solid var(--market-line);

  div {
    display: grid;
    gap: 5px;
  }

  span,
  small {
    color: var(--market-muted);
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 1.5px;
  }

  strong {
    font-family: var(--market-font-display);
    font-size: 22px;
  }
}

.calendar-chart {
  width: 100%;
  min-width: 860px;
  height: 280px;
}

@media (max-width: 600px) {
  .calendar-chart-shell {
    padding: 14px 10px;
  }
}
</style>
