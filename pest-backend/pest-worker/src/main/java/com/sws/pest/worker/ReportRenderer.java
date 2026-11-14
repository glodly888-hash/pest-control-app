package com.sws.pest.worker;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 报告 PDF 渲染器（首期实现，基于 Apache PDFBox）。
 *
 * 架构设计约定用隔离的 Chromium 渲染固定 HTML 模板；在渲染镜像就绪前，
 * 用 PDFBox（纯 Java）生成带内嵌中文字体的正式 PDF，保证：
 * - 产出真实可下载、可搜索、可复制中文的 application/pdf；
 * - sha256 可校验、可回放（同输入同输出）；
 * - 所有用户输入仅作文本绘制，天然杜绝注入。
 *
 * 后续接入 Chromium/模板引擎时替换 render() 实现即可，端口不变。
 */
public final class ReportRenderer {

    /** 报告渲染入参：来自快照正文 + 签署凭证。 */
    public record RenderInput(
            String reportNumber,
            String storeName,
            String serviceType,
            String startedAt,
            String endedAt,
            String contentJson,
            List<Signature> signatures) {
    }

    public record Signature(String role, String signerName, String signedAt, String rating, String comment) {
    }

    /** 渲染结果：PDF 字节 + sha256（小写十六进制）。 */
    public record RenderedPdf(byte[] bytes, String sha256) {
    }

    /** 中文字体候选路径（按顺序尝试，生产镜像需内置字体）。 */
    private static final String[] CN_FONT_CANDIDATES = {
            "/System/Library/Fonts/STHeiti Medium.ttc",
            "/System/Library/Fonts/Hiragino Sans GB.ttc",
            "/System/Library/Fonts/PingFang.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/arphic/uming.ttc",
    };

    public RenderedPdf render(RenderInput input) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            PDType0Font cnFont = loadCnFont(doc);
            // 无中文字体时退化用 Helvetica（中文缺字，但结构与 ASCII 正常）
            boolean hasCn = cnFont != null;
            PDType1Font fallback = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 760f;
                float left = 50f;
                float fontSize = 12f;
                float lineHeight = 18f;

                // 标题
                cs.beginText();
                if (hasCn) {
                    cs.setFont(cnFont, 18);
                    cs.newLineAtOffset(left, y);
                    cs.showText("虫害治理服务报告");
                } else {
                    cs.setFont(fallback, 18);
                    cs.newLineAtOffset(left, y);
                    cs.showText("Pest Control Service Report");
                }
                cs.endText();
                y -= 30;

                cs.beginText();
                cs.setFont(hasCn ? cnFont : fallback, fontSize);
                cs.setLeading(lineHeight);
                cs.newLineAtOffset(left, y);
                cs.showText("编号：" + input.reportNumber());
                cs.newLine();
                cs.showText("门店：" + input.storeName());
                cs.newLine();
                cs.showText("服务类型：" + input.serviceType());
                cs.newLine();
                cs.showText("服务起止：" + input.startedAt() + " ~ " + input.endedAt());
                cs.newLine();
                cs.showText("签署：");
                for (Signature s : input.signatures()) {
                    StringBuilder line = new StringBuilder()
                            .append(s.role()).append(' ').append(s.signerName())
                            .append('(').append(s.signedAt()).append(')');
                    if (s.rating() != null) {
                        line.append(" 评分").append(s.rating());
                    }
                    line.append("；");
                    cs.showText(line.toString());
                }
                cs.newLine();
                cs.showText("本报告由系统自动生成，正文见附件快照。");
                cs.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            byte[] bytes = out.toByteArray();
            return new RenderedPdf(bytes, sha256(bytes));
        } catch (IOException e) {
            throw new IllegalStateException("PDF 渲染失败: " + e.getMessage(), e);
        }
    }

    /** 加载中文字体（内嵌），找不到则退化为 Helvetica（此时中文会缺字，但不影响结构）。 */
    private PDType0Font loadCnFont(PDDocument doc) throws IOException {
        for (String path : CN_FONT_CANDIDATES) {
            File f = new File(path);
            if (!f.exists()) {
                continue;
            }
            try {
                if (path.toLowerCase().endsWith(".ttc")) {
                    // .ttc 集合：遍历所有 face，找到能渲染中文的字体
                    PDType0Font font = loadFromTtc(doc, f);
                    if (font != null) {
                        return font;
                    }
                } else {
                    PDType0Font font = PDType0Font.load(doc, f);
                    if (font.hasGlyph(0x866b)) {
                        return font;
                    }
                }
            } catch (IOException ignored) {
                // 尝试下一个候选
            }
        }
        // 兜底：无中文字体时返回 null，调用方退化 Helvetica
        return null;
    }

    /** 从 .ttc 集合中加载能渲染中文的 face。 */
    private PDType0Font loadFromTtc(PDDocument doc, File ttcFile) throws IOException {
        try (TrueTypeCollection ttc = new TrueTypeCollection(ttcFile)) {
            List<TrueTypeFont> faces = new ArrayList<>();
            ttc.processAllFonts(faces::add);
            for (TrueTypeFont face : faces) {
                try {
                    PDType0Font font = PDType0Font.load(doc, face, true);
                    if (font.hasGlyph(0x866b)) {
                        return font;
                    }
                } catch (IOException ignored) {
                    // 尝试下一个 face
                }
            }
        }
        return null;
    }

    /** 计算 SHA-256 十六进制（小写）。 */
    static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 计算 SHA-256 并返回原始字节（用于存 BINARY(32) 列）。 */
    public static byte[] sha256Bytes(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
