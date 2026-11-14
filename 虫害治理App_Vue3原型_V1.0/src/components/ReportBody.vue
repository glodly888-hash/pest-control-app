<script setup>
import { computed } from "vue";
import logo from "../assets/sheweisi-logo.png";
import PhotoField from "./PhotoField.vue";
const props = defineProps({
  record: Object,
  signatures: Object,
  reportNumber: String,
});
const data = computed(() => props.record.content);
const counted = computed(() =>
  data.value.facilities.filter(
    (f) =>
      f.result === "已检查" &&
      f.quantityKind === "明确数量" &&
      f.status === "在用",
  ),
);
const sum = (key) => counted.value.reduce((n, f) => n + Number(f[key] || 0), 0);
const date = (t) =>
  t ? new Date(t).toLocaleString("zh-CN", { hour12: false }) : "待确认";
const qty = (kind, value) =>
  kind === "明确数量" ? Number(value || 0) : "数量不明";
</script>
<template>
  <article class="report-paper">
    <div class="brand-inline">
      <img :src="logo" alt="舍为思标志" /><span>舍为思 · 虫害治理</span>
    </div>
    <h2>虫害治理服务报告</h2>
    <p class="report-caption">
      {{ reportNumber || "待发布演示稿" }} · V{{ record.version }}
    </p>
    <div class="notice mt">
      原型预览 · 演示资料<br />本页面展示内容结构，非正式服务凭证。
    </div>
    <h3>01 服务信息</h3>
    <div class="kv">
      <span>门店</span><span>{{ record.task.store }}</span>
    </div>
    <div class="kv">
      <span>编号 / 类型</span
      ><span>{{ record.task.code }} / {{ record.task.type }}</span>
    </div>
    <p class="hint">{{ record.task.address }}</p>
    <div class="kv">
      <span>到店时间</span><span>{{ date(data.checkin.time) }}</span>
    </div>
    <h3>02 服务总结与风险</h3>
    <p>{{ data.summary.text || "尚未填写服务总结" }}</p>
    <p class="mt">
      <strong>综合风险：{{ data.summary.level }}</strong>
    </p>
    <p>评定依据：{{ data.summary.basis || "待评定" }}</p>
    <p>客户配合：{{ data.summary.cooperation || "尚未填写" }}</p>
    <h3>03 分来源虫情汇总</h3>
    <table class="mini-table">
      <thead>
        <tr>
          <th>来源</th>
          <th>虫情</th>
          <th>数量</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>客户反馈</td>
          <td>
            {{
              data.intake.feedback === "有发现"
                ? data.intake.pest
                : data.intake.feedback
            }}
          </td>
          <td>
            {{
              data.intake.feedback === "有发现"
                ? qty(data.intake.quantityKind, data.intake.quantity)
                : "—"
            }}
          </td>
        </tr>
        <tr>
          <td>现场发现</td>
          <td>
            {{
              data.intake.onsite === "有发现"
                ? data.intake.onsitePest
                : data.intake.onsite
            }}
          </td>
          <td>
            {{
              data.intake.onsite === "有发现"
                ? qty(
                    data.intake.onsiteQuantityKind,
                    data.intake.onsiteQuantity,
                  )
                : "—"
            }}
          </td>
        </tr>
        <tr>
          <td>设施捕获</td>
          <td>飞虫</td>
          <td>{{ sum("flies") }}</td>
        </tr>
        <tr>
          <td>设施捕获</td>
          <td>其他</td>
          <td>{{ sum("others") }}</td>
        </tr>
        <tr>
          <td>设施捕获</td>
          <td>鼠 / 蟑螂</td>
          <td>{{ sum("rodents") }} / {{ sum("cockroaches") }}</td>
        </tr>
      </tbody>
    </table>
    <p class="hint">
      未检查
      {{
        data.facilities.filter(
          (f) => f.result === "待检查" || f.result === "未检查",
        ).length
      }}
      处；数量不明
      {{
        data.facilities.filter(
          (f) => f.result === "无法确认" || f.quantityKind === "数量不明",
        ).length
      }}
      处。各来源分别统计。
    </p>
    <h3>04 风险与整改 · {{ data.risks.length }}条</h3>
    <section v-for="r in data.risks" :key="r.id" class="report-risk">
      <strong>{{ r.title }}</strong>
      <p>{{ r.area }} · {{ r.position }} · {{ r.state }}</p>
      <p>改进建议：{{ r.advice }}</p>
      <p>本次跟进：{{ r.checked ? r.note || r.result : "尚未核查" }}</p>
      <p>责任：{{ r.responsible }} · 期限 {{ r.due }}</p>
      <PhotoField
        :model-value="r.photos"
        label="风险证据"
        readonly
      /><PhotoField
        v-if="r.followupPhotos.length"
        :model-value="r.followupPhotos"
        label="改善证据"
        readonly
      />
    </section>
    <h3>05 设施点检 · {{ data.facilities.length }}处</h3>
    <table class="mini-table">
      <thead>
        <tr>
          <th>设施 / 位置</th>
          <th>检查结果</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="f in data.facilities" :key="f.id">
          <td>{{ f.id }}<br />{{ f.type }}<br />{{ f.position }}</td>
          <td>
            {{ f.result }}<br /><template v-if="f.result === '已检查'"
              >{{ f.condition }} / {{ f.consumable }}<br /><template
                v-if="f.quantityKind === '明确数量'"
                >{{
                  f.type === "灭蝇灯" || f.type === "果蝇杯"
                    ? "飞虫 " + f.flies + " · 其他 " + f.others
                    : f.type === "粘鼠板"
                      ? "鼠 " + f.rodents
                      : "蟑螂 " + f.cockroaches
                }}</template
              ><template v-else>数量不明</template></template
            ><template v-else>{{ f.reason || "原因待填写" }}</template>
          </td>
        </tr>
      </tbody>
    </table>
    <h3>06 防治措施与用药</h3>
    <p>{{ data.treatment.type }} · {{ data.treatment.position }}</p>
    <p>{{ data.treatment.description || data.treatment.reason || "待填写" }}</p>
    <p>关联：{{ data.treatment.targets.join("、") }}</p>
    <template v-if="data.treatment.type === '药剂处理'"
      ><p>药剂：{{ data.treatment.drug }} / {{ data.treatment.form }}</p>
      <p>
        方法：{{ data.treatment.method }} · 配比
        {{ data.treatment.concentration }}
      </p>
      <p>
        {{ data.treatment.doseType }}：{{ data.treatment.dose }}
        {{ data.treatment.unit }}
      </p></template
    >
    <p v-else>本次未登记药剂使用。</p>
    <h3>07 门店静态评估</h3>
    <div
      v-for="[label, key] in [
        ['门店类型', 'storeType'],
        ['卷帘门', 'shutter'],
        ['折叠门', 'folding'],
        ['风幕机', 'air'],
        ['出风角度', 'angle'],
        ['开放式吊顶', 'ceiling'],
        ['污水井数量', 'wells'],
        ['周边绿植', 'plants'],
        ['商铺装修', 'renovation'],
        ['周边空置', 'vacancy'],
      ]"
      :key="key"
      class="kv"
    >
      <span>{{ label }}</span
      ><span>{{ data.assessment[key] }}</span>
    </div>
    <p>{{ data.assessment.note }}</p>
    <h3>08 现场确认</h3>
    <div class="report-signatures">
      <div
        v-for="[role, label] in [
          ['staff', '服务人员'],
          ['client', '客户确认人'],
        ]"
        :key="role"
      >
        <strong>{{ label }}</strong>
        <div class="signature-box">
          <img
            v-if="signatures?.[role]?.image"
            :src="signatures[role].image"
            :alt="label + '签名'"
          /><span v-else>待签字</span>
        </div>
        <p>{{ signatures?.[role]?.name || "—" }}</p>
        <p class="hint">{{ date(signatures?.[role]?.at) }}</p>
      </div>
    </div>
    <p v-if="signatures?.client?.rating" class="hint">
      客户评价：{{ signatures.client.rating }} / 5 星
    </p>
    <p v-if="signatures?.client?.comment">
      客户意见：{{ signatures.client.comment }}
    </p>
    <p class="report-caption mt">
      浙江舍为思智慧科技开发有限公司<br />提供超出预期的服务
    </p>
  </article>
</template>
