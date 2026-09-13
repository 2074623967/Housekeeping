<template>
  <AppBusinessEntryView :terminal-variant="terminalVariant" :scene-type="sceneType" />
</template>

<script setup>
import AppBusinessEntryView from "../../../app-web/src/views/BusinessEntryView.vue";

defineProps({
  terminalVariant: {
    type: String,
    default: "app"
  },
  sceneType: {
    type: String,
    default: "balance-pay"
  }
});
</script>
