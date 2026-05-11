<template>
  <div class="megagraph">
    <div>
      <!--
      <button @click="mode = (mode + 1) % 3; reset(); refilter()">
        <template v-if="mode === 0">Zoek op naam/plaats:</template>
        <template v-else-if="mode === 1">Zoek op niveau (zaal 23/24)</template>
        <template v-else-if="mode === 2">Zoek op niveau (veld nj 23)</template>
      </button>
      -->
      <label v-if="mode === 0">
        Naam: <input type="text" v-model="nameFilter" />
        Plaats: <input type="text" v-model="placeFilter" />
        <!-- Bron data: <a href="https://www.delpher.nl/">Delpher</a> en <a href="https://www.antilopen.nl/competitie-v2/">KV Antilopen</a> -->
      </label>
      <label v-else>
        <select v-model="tier" @change="group = ''; refilter()">
          <option v-for="(value, tierKey) in tiers" :key="tierKey" :value="tierKey">
            {{ tierKey }}
          </option>
        </select>
        <select v-model="group" @change="refilter" v-if="Object.keys(tiers[tier] ?? {}).length > 1">
          <option :value="''">Alles</option>
          <option v-for="(value, groupKey) in tiers[tier] ?? {}" :key="groupKey" :value="groupKey">
            {{ groupKey }}
          </option>
        </select>
      </label>
      <button @click="includeParents = !includeParents; refilter()">
        {{ includeParents ? 'Zonder fusies' : 'Met fusies' }}
      </button>
    </div>
    <div id="graph"></div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, reactive, onMounted, watch } from 'vue'
import Dygraph from 'dygraphs'
import { origins } from '@/origins'
import onMobile from '@/onMobile'
import debounce from 'lodash.debounce'

export default defineComponent({
  name: 'MegaGraph',
  setup() {
    // Reactive references
    let graph!: Dygraph;
    const labels = reactive(new Map<string, number>());
    const mode = ref(0);
    const tiers = reactive<{ [key: string]: { [key: string]: string[] } }>({});
    const tier = ref('kl');
    const group = ref('');
    const includeParents = ref(true);
    const nameFilter = ref('');
    const placeFilter = ref('');
    const theOrigins = reactive(new Map<string, string[]>());
    type DygraphTouchContext = {
      initializeMouseDown: (event: MouseEvent, g: Dygraph, context: DygraphTouchContext) => void
    }

    // Methods
    function reset() {
      tier.value = 'kl';
      nameFilter.value = '';
      placeFilter.value = '';
      if (mode.value === 0) {
        nameFilter.value = '';
        placeFilter.value = '';
      } else {
        // tiers = mode.value === 1 ? indoorTiers : outdoor23nj;
        const [firstTier] = Object.keys(tiers)
        if (firstTier) {
          tier.value = firstTier
        }
        group.value = ''
      }
    }

    function refilter() {
      if (graph) {
        const sss = new Set<number>();
        const vs = graph.visibility();
        if (mode.value === 0) {
          graph.getLabels()?.forEach((t, i) => {
            if (i !== 0 && t !== 'sd' && t !== 'teams') {
              const show = new RegExp(
                `^.*${nameFilter.value}.*\\(.*${placeFilter.value}.*\\)`,
                'i'
              ).test(t);
              if (show) {
                if (includeParents.value) {
                  const origins = theOrigins.get(t) || [];
                  for (const s of origins) {
                    sss.add(labels.get(s) ?? -1);
                  }
                } else {
                  sss.add(i - 1);
                }
              }
            }
          });
        } else {
          const currentTier = tiers[tier.value];
          if (currentTier) {
            for (const [p, ts] of Object.entries(currentTier)) {
              for (const t of ts) {
                if (group.value && group.value !== p) {
                  continue;
                }

                const i = labels.get(t) ?? -1;
                if (includeParents.value) {
                  for (const s of theOrigins.get(t) || []) {
                    sss.add(labels.get(s) ?? -1);
                  }
                } else {
                  sss.add(i);
                }
              }
            }
          }
        }
        const ss: number[] = [];
        const hs: number[] = [];

        vs.forEach((b, i) => {
          const bb = sss.has(i);
          if (b !== bb) {
            (bb ? ss : hs).push(i);
          }
        });

        graph.setVisibility(ss, true);
        graph.setVisibility(hs, false);
      }
    }

    // Debounce the refilter function
    const debouncedRefilter = debounce(refilter, 200);

    // Watchers
    watch([nameFilter, placeFilter], () => {
      debouncedRefilter();
    });

    // Lifecycle hook
    onMounted(() => {
      graph = new Dygraph('graph', 'graph.csv', {
        connectSeparatedPoints: true,
        highlightSeriesOpts: {
          strokeWidth: 1.5,
          strokeBorderWidth: 1,
          highlightCircleSize: 5,
          strokeBorderColor: window.matchMedia("(prefers-color-scheme: dark)").matches ? '#ffffff' : '#000000',
        },
        interactionModel: onMobile()
          ? {
            mousedown(event: MouseEvent, g: Dygraph, context: DygraphTouchContext) {
              context.initializeMouseDown(event, g, context);
            },
          }
          : null,
        legend: 'always',
        labelsShowZeroValues: false,
        stepPlot: true,
        colorValue: window.matchMedia("(prefers-color-scheme: dark)").matches ? 1 : 0.7,
        colorSaturation: 0.5,
        strokeWidth: 0.5,
        highlightSeriesBackgroundAlpha: 1,
        valueRange: [300, 2700],
      });

      for (const origin of origins.split('\n')) {
        const os = origin.split(',')
        const key = os[0]?.trim()
        if (!key) continue
        theOrigins.set(key, os.slice(1))
      }

      graph.ready(() => {
        graph?.getLabels()?.forEach((l, i) => {
          labels.set(l, i - 1);
        });
      });
    });

    // Return variables and methods to the template
    return {
      mode,
      tiers,
      tier,
      group,
      includeParents,
      nameFilter,
      placeFilter,
      reset,
      refilter,
    };
  },
});
</script>

<style>
.megagraph .dygraph-legend {
  margin-left: 64px;
}
.megagraph .dygraph-legend > span {
  display: none;
}

.megagraph .dygraph-legend > span.highlight {
  display: inline;
}

.megagraph #graph {
  position: absolute;
  z-index: -1;
  left: 10px;
  right: 10px;
  top: 58px;
  bottom: 0px;
}
</style>
