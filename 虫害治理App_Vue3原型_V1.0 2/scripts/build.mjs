import { build } from "vite";
import vue from "@vitejs/plugin-vue";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { pages } from "../src/data/pages.js";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const output = resolve(root, "独立原型");
const arg = process.argv[2];
if (arg && !pages.some((p) => p.id === arg))
  throw new Error(
    "页面不存在：" + arg + "。可选：" + pages.map((p) => p.id).join(", "),
  );
await Promise.all(
  ["src/entries", "pages", "独立原型"].map((d) =>
    mkdir(resolve(root, d), { recursive: true }),
  ),
);
const html = (title, body, head = "") =>
  `<!doctype html>\n<html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${title} · 舍为思虫控原型</title>${head}</head><body>${body}</body></html>`;
for (const page of pages) {
  const entry = `import { mountPage } from '../bootstrap.js';\nimport Page from '../pages/${page.component}.vue';\n\nmountPage('${page.id}', Page);\n`;
  await writeFile(resolve(root, "src/entries", page.id + ".js"), entry);
  await writeFile(
    resolve(root, "pages", page.file),
    html(
      page.title,
      `<div id="app"></div><script type="module" src="../src/entries/${page.id}.js"></script>`,
    ),
  );
}
for (const page of pages.filter((p) => !arg || p.id === arg)) {
  const bundle = await build({
    root,
    configFile: false,
    plugins: [vue()],
    logLevel: "error",
    define: { "process.env.NODE_ENV": JSON.stringify("production") },
    build: {
      write: false,
      emptyOutDir: false,
      minify: true,
      cssCodeSplit: false,
      lib: {
        entry: resolve(root, "src/entries", page.id + ".js"),
        name: "Sheweisi_" + page.id,
        formats: ["iife"],
        fileName: () => page.id + ".js",
      },
    },
  });
  const assets = (Array.isArray(bundle) ? bundle : [bundle]).flatMap(
    (b) => b.output,
  );
  const js = assets
    .filter((a) => a.type === "chunk")
    .map((a) => a.code)
    .join("\n");
  const css = assets
    .filter((a) => a.type === "asset" && a.fileName.endsWith(".css"))
    .map((a) => String(a.source))
    .join("\n");
  if (!js || !css) throw new Error("缺少页面代码或样式：" + page.id);
  const content = html(
    page.title,
    `<div id="app"></div><script>${js.replace(/<\/script/gi, "<\\/script")}</script>`,
    `<style>${css}</style>`,
  );
  await writeFile(resolve(output, page.file), content);
  console.log(
    page.file + " · " + Math.round(Buffer.byteLength(content) / 1024) + " KB",
  );
}
const logo = await readFile(resolve(root, "src/assets/sheweisi-logo.png"));
const catalog = (prefix) =>
  html(
    "页面总览",
    `<header><img src="data:image/png;base64,${logo.toString("base64")}" alt="舍为思Logo"><div><p>舍为思智慧科技 / Vue 3</p><h1>虫控作业 · 产品原型</h1><p>提供超出预期的服务</p></div></header><main><section class="intro"><div><h2>从现场作业，到可追溯的服务。</h2><p>20 个独立页面，按舍为思 VI 统一设计。每页内含 Vue 3、样式与品牌资源，可单独打开。</p><p>演示数据仅保存在本机；定位、同步与发布为交互演示。完整跨页体验建议使用本地预览服务。</p></div><a class="primary" href="${prefix}02-home.html">进入工作台 →</a></section>${[
      ...new Set(pages.map((p) => p.group)),
    ]
      .map(
        (group) =>
          `<section><h2>${group}</h2><div class="grid">${pages
            .filter((p) => p.group === group)
            .map(
              (p) =>
                `<a class="card" href="${prefix}${p.file}"><span>${String(p.number).padStart(2, "0")}</span><h3>${p.title}</h3><p>${p.description}</p><small>${p.component}.vue</small></a>`,
            )
            .join("")}</div></section>`,
      )
      .join(
        "",
      )}</main><footer>浙江舍为思智慧科技开发有限公司 · 交互原型 V1.0 · 2026-11-12</footer>`,
    `<style>*{box-sizing:border-box}body{margin:0;background:#F5F8FA;color:#333;font:14px/1.8 'Source Han Sans SC','Microsoft YaHei','PingFang SC',Arial,sans-serif}header,main,footer{max-width:1200px;margin:auto;padding:40px}header{display:flex;gap:24px;align-items:center}header img{width:72px;height:72px}header p{color:#666;margin:0}h1{font-size:32px;line-height:1.4;margin:4px 0;color:#003D7A}h2{font-size:24px;color:#003D7A}h3{font-size:18px;margin:8px 0;color:#003D7A}main{padding-top:0}.intro{display:flex;align-items:center;gap:40px;background:white;border:1px solid #dce7ef;border-radius:8px;padding:24px;margin-bottom:40px}.intro>div{flex:1}.intro h2{margin:0 0 8px}.intro p{margin:8px 0;color:#666}.primary{display:block;background:#003D7A;border-radius:4px;color:white;padding:12px 24px;white-space:nowrap;text-decoration:none}.primary:hover{background:#4A9FD8;color:#003D7A}.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:20px}section{margin-bottom:40px}.card{display:block;background:#fff;border:1px solid #dbe7ef;border-radius:8px;padding:24px;text-decoration:none;color:#333;box-shadow:0 2px 8px #003d7a10}.card:hover{border-color:#4A9FD8;box-shadow:0 4px 16px #003d7a20}.card>span{color:#4A9FD8;font-size:18px;font-weight:bold}.card p{color:#666}.card small{font-size:12px;color:#66829a}footer{color:#666;font-size:12px;border-top:1px solid #dce7ef}a:focus-visible{outline:3px solid #4A9FD8;outline-offset:4px}@media(max-width:1199px){.grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:767px){header,main,footer{padding:24px 16px}header{gap:16px}header img{width:48px;height:48px}h1{font-size:24px}h2{font-size:20px}.intro{flex-direction:column;gap:16px;align-items:stretch}.grid{grid-template-columns:1fr}.card small,footer{font-size:14px}}</style>`,
  );
await writeFile(resolve(output, "index.html"), catalog(""));
await writeFile(resolve(root, "index.html"), catalog("pages/"));
console.log(`已生成${arg ? "所选页面" : "20个页面"}；浏览 独立原型/index.html`);
