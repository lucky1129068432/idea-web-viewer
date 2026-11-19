package com.idea.webviewer.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.util.ui.UIUtil;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.CefSettings;
import org.cef.handler.CefDisplayHandler;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler.ErrorCode;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.network.CefRequest;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class JBCefBrowserWrapper extends JPanel implements Disposable {

    private final JBCefBrowser browser;
    private boolean isDarkTheme;
    private boolean disposed = false;
    private UrlChangeListener urlChangeListener;
    private TitleChangeListener titleChangeListener;
    private OpenNewTabListener openNewTabListener;

    private static final String PAGE_PADDING_CSS =
        "(function() {" +
        "  var id = '__webviewer_page_padding__';" +
        "  if (document.getElementById(id)) return;" +
        "  var style = document.createElement('style');" +
        "  style.id = id;" +
        "  style.textContent = 'html { padding: 0 10px !important; box-sizing: border-box !important; } body { margin-left: auto !important; margin-right: auto !important; max-width: 100% !important; }';" +
        "  document.head.appendChild(style);" +
        "})();";

    public JBCefBrowserWrapper() {
        super(new BorderLayout());
        // 插件不再硬依赖 com.intellij.modules.jcef（该 id 在 241/242 上根本不存在），
        // 因此这里必须能优雅降级，而不是抛 NoClassDefFoundError。
        if (!JBCefApp.isSupported()) {
            browser = null;
            JLabel hint = new JLabel("当前 IDE 未启用 JCEF，无法内嵌浏览器", SwingConstants.CENTER);
            hint.setEnabled(false);
            add(hint, BorderLayout.CENTER);
            return;
        }
        browser = new JBCefBrowser("about:blank");
        add(browser.getComponent(), BorderLayout.CENTER);
        setupRequestHandler();
        setupLifeSpanHandler();
        setupDarkModeSupport();
        setupTitleHandler();
    }

    /** JCEF 是否可用；不可用时所有浏览相关操作都变成空操作。 */
    private boolean isBrowserAvailable() {
        return browser != null;
    }

    /**
     * 拦截 CEF 原生弹窗，避免 window.open / target=_blank 等请求弹出脱离编辑器的独立窗口。
     * 由 CEF 的 onBeforePopup 统一接管，可覆盖 iframe、页面加载过程中等 JS 注入覆盖不到的路径。
     */
    private void setupLifeSpanHandler() {
        browser.getJBCefClient().addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser cefBrowser, CefFrame frame, String targetUrl, String targetFrameName) {
                // 返回 true 表示弹窗由我们处理，CEF 不再创建新的顶层窗口
                if (targetUrl != null && !targetUrl.isEmpty()) {
                    OpenNewTabListener listener = openNewTabListener;
                    if (listener != null) {
                        listener.onOpenNewTab(targetUrl);
                    }
                }
                return true;
            }
        }, browser.getCefBrowser());
    }

    private void setupRequestHandler() {
        browser.getJBCefClient().addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request, boolean userGesture, boolean isRedirect) {
                String url = request.getURL();
                if (url.startsWith("webviewer://")) {
                    if (url.startsWith("webviewer://open_new_tab?")) {
                        String params = url.substring("webviewer://open_new_tab?".length());
                        String[] parts = params.split("&");
                        for (String part : parts) {
                            if (part.startsWith("url=")) {
                                String targetUrl = part.substring(4);
                                try {
                                    targetUrl = java.net.URLDecoder.decode(targetUrl, "UTF-8");
                                } catch (java.io.UnsupportedEncodingException e) {
                                }
                                if (openNewTabListener != null) {
                                    openNewTabListener.onOpenNewTab(targetUrl);
                                }
                                break;
                            }
                        }
                    }
                    return true;
                }
                return false;
            }
        }, browser.getCefBrowser());
    }

    private void setupTitleHandler() {
        browser.getJBCefClient().addDisplayHandler(new CefDisplayHandler() {
            @Override
            public void onTitleChange(CefBrowser browser, String title) {
                if (title != null && !title.isEmpty()) {
                    if (titleChangeListener != null) {
                        SwingUtilities.invokeLater(() -> titleChangeListener.onTitleChanged(title));
                    }
                }
            }

            @Override
            public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {}

            @Override
            public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level, String message, String source, int line) {
                return false;
            }

            @Override
            public boolean onTooltip(CefBrowser browser, String text) {
                return false;
            }

            @Override
            public void onStatusMessage(CefBrowser browser, String value) {}

            @Override
            public boolean onCursorChange(CefBrowser browser, int cursorType) {
                return false;
            }

            @Override
            public void onFullscreenModeChange(CefBrowser browser, boolean fullscreen) {}
        }, browser.getCefBrowser());
    }

    public JComponent getComponent() {
        return this;
    }

    public void loadUrl(String url) {
        if (!isBrowserAvailable()) {
            return;
        }
        String target = normalizeUrl(url);
        if (target.isEmpty()) {
            return;
        }
        if (isReaderMode) {
            isReaderMode = false;
            exitReaderMode();
        }
        browser.loadURL(target);
    }

    /** 补全地址：只有没写协议时才补 https，about:/file:/data: 等保持原样。 */
    private static String normalizeUrl(String url) {
        String trimmed = url == null ? "" : url.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.contains("://")
            || trimmed.startsWith("about:")
            || trimmed.startsWith("file:")
            || trimmed.startsWith("data:")
            || trimmed.startsWith("view-source:")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    public void setDarkTheme(boolean dark) {
        this.isDarkTheme = dark;
        applyDarkMode();
        if (isReaderMode) {
            injectReaderMode();
        }
    }

    public void goBack() {
        if (canGoBack()) {
            browser.getCefBrowser().goBack();
        }
    }

    public void goForward() {
        if (canGoForward()) {
            browser.getCefBrowser().goForward();
        }
    }

    public void refresh() {
        if (!isBrowserAvailable()) {
            return;
        }
        browser.getCefBrowser().reload();
    }

    public void convertToSimplifiedChinese() {
        if (disposed) return;
        String openccScript = loadOpenccScript();
        if (openccScript != null) {
            executeJavaScript(openccScript);
            executeJavaScript("if (window.OpenCC) { OpenCC.convertDocument(); }");
        }
    }

    private String loadOpenccScript() {
        try (InputStream is = getClass().getResourceAsStream("/opencc.js")) {
            if (is != null) {
                byte[] bytes = is.readAllBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean canGoBack() {
        return isBrowserAvailable() && browser.getCefBrowser().canGoBack();
    }

    public boolean canGoForward() {
        return isBrowserAvailable() && browser.getCefBrowser().canGoForward();
    }

    public String getUrl() {
        if (!isBrowserAvailable() || browser.getCefBrowser() == null) {
            return "";
        }
        return browser.getCefBrowser().getURL();
    }

    public void setUrlChangeListener(UrlChangeListener listener) {
        this.urlChangeListener = listener;
    }

    public interface UrlChangeListener {
        void onUrlChanged(String url);
    }

    public interface TitleChangeListener {
        void onTitleChanged(String title);
    }

    public void setTitleChangeListener(TitleChangeListener listener) {
        this.titleChangeListener = listener;
    }

    private void setupDarkModeSupport() {
        browser.getJBCefClient().addLoadHandler(new CefLoadHandler() {
            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                injectPagePadding();
                injectPopupInterceptor();
                applyDarkMode();
                notifyUrlChanged();
                if (isReaderMode) {
                    injectReaderMode();
                }
            }

            @Override
            public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
                notifyUrlChanged();
            }

            @Override
            public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
                // 新页面加载会重建 DOM，需允许重新注入亮度与配色
                if (frame != null && frame.isMain()) {
                    lastAppliedFilter = null;
                    colorsInjected = false;
                }
                notifyUrlChanged();
            }
        }, browser.getCefBrowser());
    }

    private void injectPagePadding() {
        if (disposed) return;
        executeJavaScript(PAGE_PADDING_CSS);
    }

    private void injectPopupInterceptor() {
        if (disposed) return;
        String script =
            "(function() {" +
            "  if (window.__webviewer_link_handler__) return;" +
            "  window.__webviewer_link_handler__ = true;" +
            "  document.addEventListener('click', function(e) {" +
            "    var el = e.target.closest('a');" +
            "    if (el && el.href) {" +
            "      if (e.ctrlKey || e.metaKey) {" +
            "        e.preventDefault();" +
            "        window.location.href = 'webviewer://open_new_tab?url=' + encodeURIComponent(el.href);" +
            "      } else if (el.getAttribute('target') === '_blank') {" +
            "        e.preventDefault();" +
            "        window.location.href = el.href;" +
            "      }" +
            "    }" +
            "  });" +
            "  var originalOpen = window.open;" +
            "  window.open = function(url) {" +
            "    if (url) { window.location.href = url; }" +
            "    return null;" +
            "  };" +
            "})();";
        executeJavaScript(script);
    }

    private void notifyUrlChanged() {
        if (urlChangeListener != null) {
            String url = getUrl();
            if (url != null && !url.isEmpty()) {
                urlChangeListener.onUrlChanged(url);
            }
        }
    }

    /** 默认最暗（5 = brightness(0.4)），对应亮度滑块最左端。 */
    private int brightnessLevel = 5;
    private String customBgColor = "#1e1e1e";
    private String customTextColor = "#d4d4d4";
    private boolean colorsModified = false;
    private boolean isReaderMode = false;
    private String lastAppliedFilter = null;
    private boolean colorsInjected = false;

    public void setBrightnessLevel(int level) {
        this.brightnessLevel = level;
        applyBrightness();
        if (isReaderMode) {
            injectReaderMode();
        }
    }

    public void setCustomColors(String bgColor, String textColor) {
        this.customBgColor = bgColor;
        this.customTextColor = textColor;
        this.colorsModified = true;
        applyBrightness();
        if (isReaderMode) {
            injectReaderMode();
        }
    }

    public void resetCustomColors() {
        this.colorsModified = false;
        clearCustomColors();
        applyBrightness();
    }

    private void applyDarkMode() {
        applyBrightness();
    }

    private void applyBrightness() {
        if (disposed) return;
        String filter = brightnessFilter(brightnessLevel);
        // 亮度值未变化时不再重复注入，避免根节点 filter 触发整页重绘造成卡顿
        if (!filter.equals(lastAppliedFilter)) {
            lastAppliedFilter = filter;
            executeJavaScript("document.documentElement.style.filter = '" + filter + "';");
        }
        if (isDarkTheme || colorsModified) {
            injectCustomColors(customBgColor, customTextColor);
        } else if (colorsInjected) {
            clearCustomColors();
        }
    }

    /** 亮度等级（-5..5，0 为原始亮度）到 CSS filter 的映射。 */
    private static String brightnessFilter(int level) {
        return switch (level) {
            case 1 -> "brightness(0.9)";
            case 2 -> "brightness(0.8)";
            case 3 -> "brightness(0.7)";
            case 4 -> "brightness(0.55)";
            case 5 -> "brightness(0.4)";
            case -1 -> "brightness(1.1)";
            case -2 -> "brightness(1.2)";
            case -3 -> "brightness(1.3)";
            case -4 -> "brightness(1.4)";
            case -5 -> "brightness(1.5)";
            default -> "";
        };
    }

    private void injectCustomColors(String bgColor, String textColor) {
        if (disposed) return;
        colorsInjected = true;
        String css =
            "html, body, div, span, applet, object, iframe, h1, h2, h3, h4, h5, h6, p, blockquote, pre, a, abbr, acronym, address, big, cite, code, del, dfn, em, img, ins, kbd, q, s, samp, small, strike, strong, sub, sup, tt, var, b, u, i, center, dl, dt, dd, ol, ul, li, fieldset, form, label, legend, table, caption, tbody, tfoot, thead, tr, th, td, article, aside, canvas, details, embed, figure, figcaption, footer, header, hgroup, menu, nav, output, ruby, section, summary, time, mark, audio, video { background-color: " + bgColor + " !important; color: " + textColor + " !important; } " +
            "body { background-color: " + bgColor + " !important; } " +
            "a { color: #6bb3ff !important; } " +
            "a:hover { color: #8bc3ff !important; }";
        // 样式内容未变化时直接跳过，避免「移除再重建 style」触发重复的全页样式重算
        String script =
            "(function() {" +
            "  var id = '__webviewer_custom_colors__';" +
            "  var css = '" + css + "';" +
            "  var existing = document.getElementById(id);" +
            "  if (existing) {" +
            "    if (existing.textContent !== css) existing.textContent = css;" +
            "    return;" +
            "  }" +
            "  var style = document.createElement('style');" +
            "  style.id = id;" +
            "  style.textContent = css;" +
            "  document.head.appendChild(style);" +
            "})();";
        executeJavaScript(script);
    }

    private void clearCustomColors() {
        if (disposed) return;
        colorsInjected = false;
        String script =
            "(function() {" +
            "  var existing = document.getElementById('__webviewer_custom_colors__');" +
            "  if (existing) existing.remove();" +
            "})();";
        executeJavaScript(script);
    }

    private void executeJavaScript(String script) {
        if (disposed || !isBrowserAvailable()) return;
        UIUtil.invokeLaterIfNeeded(() -> {
            if (!disposed && browser.getCefBrowser() != null) {
                browser.getCefBrowser().executeJavaScript(script, browser.getCefBrowser().getURL(), 0);
            }
        });
    }

    public void toggleReaderMode() {
        if (disposed) return;
        isReaderMode = !isReaderMode;
        if (isReaderMode) {
            injectReaderMode();
        } else {
            exitReaderMode();
        }
    }

    public boolean isReaderMode() {
        return isReaderMode;
    }

    private void injectReaderMode() {
        if (disposed) return;
        String bg = customBgColor;
        String fg = customTextColor;
        String titleColor = fg;
        String hrColor = fg.equals("#d4d4d4") || fg.equals("#c0c0c0") ? "#444" : "#aaa";
        String linkColor = "#6bb3ff";
        String quoteColor = fg.equals("#d4d4d4") || fg.equals("#c0c0c0") ? "#888" : "#aaa";
        String codeBg = bg.equals("#1e1e1e") ? "#2d2d2d" : "#f5f5f5";
        String thBg = bg.equals("#1e1e1e") ? "#333" : "#f0f0f0";
        String borderColor = hrColor;

        String script =
            "(function() {" +
            "  var existing = document.getElementById('__webviewer_reader_mode__');" +
            "  if (existing) existing.remove();" +
            "  if (window.__webviewer_original_filter__ === undefined) {" +
            "    window.__webviewer_original_filter__ = document.documentElement.style.filter || '';" +
            "  }" +
            "  document.documentElement.style.filter = '';" +
            "  window.__webviewer_original_overflow__ = document.body.style.overflow;" +
            "  function extractContent() {" +
            "    var candidates = [];" +
            "    var elements = document.querySelectorAll('article, [role=main], main, .post-content, .entry-content, .article-content, .content, #content, #main-content, .post, .entry');" +
            "    for (var i = 0; i < elements.length; i++) {" +
            "      var el = elements[i];" +
            "      var text = el.innerText || el.textContent;" +
            "      if (text && text.length > 200) {" +
            "        candidates.push({el: el, score: text.length});" +
            "      }" +
            "    }" +
            "    if (candidates.length === 0) {" +
            "      var all = document.querySelectorAll('div, section');" +
            "      for (var i = 0; i < all.length; i++) {" +
            "        var text = all[i].innerText || all[i].textContent;" +
            "        if (text && text.length > 300) {" +
            "          candidates.push({el: all[i], score: text.length});" +
            "        }" +
            "      }" +
            "    }" +
            "    candidates.sort(function(a, b) { return b.score - a.score; });" +
            "    return candidates.length > 0 ? candidates[0].el.innerHTML : document.body.innerHTML;" +
            "  }" +
            "  var content = extractContent();" +
            "  var title = document.title || 'Reading';" +
            "  var overlay = document.createElement('div');" +
            "  overlay.id = '__webviewer_reader_mode__';" +
            "  overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;overflow-y:auto;z-index:999999;background:" + bg + " !important;color:" + fg + " !important;font-family: Georgia, \"Noto Serif\", serif;line-height:1.8;font-size:18px;';" +
            "  var container = document.createElement('div');" +
            "  container.style.cssText = 'max-width:680px;margin:40px auto;padding:0 24px;background:" + bg + " !important;color:" + fg + " !important;';" +
            "  var h1 = document.createElement('h1');" +
            "  h1.textContent = title;" +
            "  h1.style.cssText = 'font-size:28px;font-weight:600;margin-bottom:12px;line-height:1.3;color:" + titleColor + " !important;background:" + bg + " !important;font-family: system-ui, -apple-system, sans-serif;';" +
            "  var hr = document.createElement('hr');" +
            "  hr.style.cssText = 'border:none;border-top:1px solid " + hrColor + ";margin:20px 0;';" +
            "  var article = document.createElement('div');" +
            "  article.innerHTML = content;" +
            "  article.style.cssText = 'word-wrap:break-word;background:" + bg + " !important;color:" + fg + " !important;';" +
            "  var style = document.createElement('style');" +
            "  style.textContent = 'html, body { background:" + bg + " !important; color:" + fg + " !important; } " +
            "    #__webviewer_reader_mode__, #__webviewer_reader_mode__ * { background-color:" + bg + " !important; color:" + fg + " !important; } " +
            "    #__webviewer_reader_mode__ img, #__webviewer_reader_mode__ figure, #__webviewer_reader_mode__ video { max-width:100%; height:auto; display:block; margin:20px auto; background-color:transparent !important; } " +
            "    #__webviewer_reader_mode__ p { margin:1em 0; color:" + fg + " !important; } " +
            "    #__webviewer_reader_mode__ h1, #__webviewer_reader_mode__ h2, #__webviewer_reader_mode__ h3, #__webviewer_reader_mode__ h4 { margin:1.5em 0 0.5em; line-height:1.4; color:" + titleColor + " !important; font-family: system-ui, -apple-system, sans-serif; } " +
            "    #__webviewer_reader_mode__ a { color:" + linkColor + " !important; text-decoration:none; } " +
            "    #__webviewer_reader_mode__ a:hover { text-decoration:underline; } " +
            "    #__webviewer_reader_mode__ blockquote { border-left:4px solid " + borderColor + "; margin:1em 0; padding-left:16px; color:" + quoteColor + " !important; } " +
            "    #__webviewer_reader_mode__ pre, #__webviewer_reader_mode__ code { background-color:" + codeBg + " !important; color:" + fg + " !important; padding:2px 6px; border-radius:3px; font-family: monospace; font-size:0.9em; } " +
            "    #__webviewer_reader_mode__ pre { padding:12px; overflow-x:auto; } " +
            "    #__webviewer_reader_mode__ table { width:100%; border-collapse:collapse; margin:1em 0; } " +
            "    #__webviewer_reader_mode__ td, #__webviewer_reader_mode__ th { border:1px solid " + borderColor + "; padding:8px; color:" + fg + " !important; } " +
            "    #__webviewer_reader_mode__ th { background-color:" + thBg + " !important; } " +
            "    #__webviewer_reader_mode__ td { background-color:" + bg + " !important; }';" +
            "  container.appendChild(h1);" +
            "  container.appendChild(hr);" +
            "  container.appendChild(article);" +
            "  overlay.appendChild(style);" +
            "  overlay.appendChild(container);" +
            "  document.body.style.overflow = 'hidden';" +
            "  document.body.appendChild(overlay);" +
            "})();";
        executeJavaScript(script);
    }

    private void exitReaderMode() {
        if (disposed) return;
        String script =
            "(function() {" +
            "  var overlay = document.getElementById('__webviewer_reader_mode__');" +
            "  if (overlay) overlay.remove();" +
            "  if (window.__webviewer_original_overflow__ !== undefined) {" +
            "    document.body.style.overflow = window.__webviewer_original_overflow__;" +
            "  }" +
            "  if (window.__webviewer_original_filter__ !== undefined) {" +
            "    document.documentElement.style.filter = window.__webviewer_original_filter__;" +
            "  }" +
            "})();";
        executeJavaScript(script);
    }

    @Override
    public void dispose() {
        disposed = true;
        if (browser != null) {
            Disposer.dispose(browser);
        }
    }

    public interface OpenNewTabListener {
        void onOpenNewTab(String url);
    }

    public void setOpenNewTabListener(OpenNewTabListener listener) {
        this.openNewTabListener = listener;
    }
}
