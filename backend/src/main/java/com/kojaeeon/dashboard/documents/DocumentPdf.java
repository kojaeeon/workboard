package com.kojaeeon.dashboard.documents;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
class DocumentPdf {

  private static final int MAX_HTML_BYTES = 10 * 1024 * 1024;
  private final String chromium;
  private final String stylesheet;

  DocumentPdf(@Value("${DASHBOARD_PDF_CHROMIUM_PATH:}") String chromium) throws IOException {
    this.chromium = chromium;
    try (var input = new ClassPathResource("document-pdf.css").getInputStream()) {
      this.stylesheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  static String filename(String title) {
    String stem = title == null ? "" : title.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").strip();
    stem = stem.replaceFirst("(?i)\\.(md|markdown)$", "").replaceFirst("[. ]+$", "");
    if (stem.isBlank()) stem = "document";
    return stem.substring(0, stem.offsetByCodePoints(0, Math.min(100, stem.codePointCount(0, stem.length())))) + ".pdf";
  }

  synchronized byte[] render(String title, String html) {
    if (html == null || html.isBlank()) throw new IllegalArgumentException("PDF로 출력할 본문이 없습니다.");
    if (html.length() > MAX_HTML_BYTES || html.getBytes(StandardCharsets.UTF_8).length > MAX_HTML_BYTES) {
      throw new IllegalArgumentException("PDF 출력용 본문과 이미지의 합계가 10MB를 넘습니다.");
    }

    var options = new BrowserType.LaunchOptions().setHeadless(true).setTimeout(30_000);
    if (!chromium.isBlank()) options.setExecutablePath(Path.of(chromium));
    try (var playwright = Playwright.create(); var browser = playwright.chromium().launch(options)) {
      var page = browser.newPage(new Browser.NewPageOptions()
        .setJavaScriptEnabled(false)
        .setOffline(true)
        .setServiceWorkers(ServiceWorkerPolicy.BLOCK));
      page.route("**/*", Route::abort);
      page.setContent("""
        <!doctype html>
        <html lang="ko">
        <head>
          <meta charset="utf-8">
          <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src data:">
          <title>%s</title>
          <style>%s</style>
        </head>
        <body>%s</body>
        </html>
        """.formatted(HtmlUtils.htmlEscape(title == null ? "" : title), stylesheet, html));
      return page.pdf(new Page.PdfOptions().setPreferCSSPageSize(true).setPrintBackground(true));
    }
  }
}
