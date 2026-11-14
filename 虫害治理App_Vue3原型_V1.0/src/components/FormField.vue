<script setup>
const model = defineModel();
defineProps({
  label: String,
  type: { type: String, default: "text" },
  options: Array,
  placeholder: String,
  required: Boolean,
  min: [String, Number],
  max: [String, Number],
  rows: { type: Number, default: 3 },
});
</script>
<template>
  <label class="field"
    ><span>{{ label }}<b v-if="required" aria-hidden="true"> *</b></span
    ><select v-if="options" v-model="model" :required="required">
      <option
        v-for="o in options"
        :key="typeof o === 'object' ? o.value : o"
        :value="typeof o === 'object' ? o.value : o"
      >
        {{ typeof o === "object" ? o.label : o }}
      </option></select
    ><textarea
      v-else-if="type === 'textarea'"
      v-model="model"
      :placeholder="placeholder"
      :required="required"
      :rows="rows"
    ></textarea
    ><input
      v-else
      v-model="model"
      :type="type"
      :placeholder="placeholder"
      :required="required"
      :min="min"
      :max="max"
      :autocomplete="type === 'password' ? 'off' : undefined"
  /></label>
</template>
