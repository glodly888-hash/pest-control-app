import { createApp, markRaw } from "vue";
import AppShell from "./AppShell.vue";
import PageFrame from "./components/PageFrame.vue";
import Icon from "./components/Icon.vue";
import FormField from "./components/FormField.vue";
import Segmented from "./components/Segmented.vue";
import PhotoField from "./components/PhotoField.vue";
import EditorGuard from "./components/EditorGuard.vue";
import TaskCard from "./components/TaskCard.vue";
import SignaturePad from "./components/SignaturePad.vue";
import "./styles/tokens.css";
import "./styles/app.css";
export function mountPage(pageId, component) {
  const app = createApp(AppShell, {
    pageId,
    pageComponent: markRaw(component),
  });
  for (const [name, c] of Object.entries({
    PageFrame,
    Icon,
    FormField,
    Segmented,
    PhotoField,
    EditorGuard,
    TaskCard,
    SignaturePad,
  }))
    app.component(name, c);
  app.mount("#app");
}
