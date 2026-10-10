<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { X } from '@lucide/vue'

const props = defineProps({ open: Boolean, title: { type: String, required: true }, size: { type: String, default: 'medium' }, side: { type: Boolean, default: false } })
const emit = defineEmits(['close'])
const surface = ref(null)
function hide() { emit('close') }
function keys(event) { if (event.key === 'Escape') hide() }
watch(() => props.open, async value => { if (value) { await nextTick(); if (!surface.value?.open) surface.value?.showModal(); document.addEventListener('keydown', keys); surface.value?.querySelector('input,button,select,textarea,[tabindex]:not([tabindex="-1"])')?.focus() } else { document.removeEventListener('keydown', keys); if (surface.value?.open) surface.value.close() } }, { immediate: true })
onBeforeUnmount(() => document.removeEventListener('keydown', keys))
</script>

<template>
  <dialog ref="surface" class="sheet" :class="[`sheet--${size}`, { 'sheet--side': side }]" :aria-label="title" @click.self="hide" @cancel.prevent="hide">
    <header class="sheet__head"><div><span class="sheet__overline">FRESH MARKET · 鲜集</span><h2>{{ title }}</h2></div><button class="icon-button" aria-label="关闭" @click="hide"><X :size="19" /></button></header>
    <slot />
  </dialog>
</template>
