package com.idea.webviewer.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.util.ui.UIUtil;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.CefSettings;
import org.cef.handler.CefDisplayHandler;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandler.ErrorCode;
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
    private String currentPageTitle = "";

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
        browser = new JBCefBrowser("about:blank");
        add(browser.getComponent(), BorderLayout.CENTER);
        setupDarkModeSupport();
        setupTitleHandler();
    }

    private void setupTitleHandler() {
        browser.getJBCefClient().addDisplayHandler(new CefDisplayHandler() {
            @Override
            public void onTitleChange(CefBrowser browser, String title) {
                if (title != null && !title.isEmpty()) {
                    currentPageTitle = title;
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
        }, browser.getCefBrowser());
    }

    public JComponent getComponent() {
        return this;
    }

    public void loadUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        browser.loadURL(url);
    }

    public void setDarkTheme(boolean dark) {
        this.isDarkTheme = dark;
        applyDarkMode();
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
        return browser.getCefBrowser().canGoBack();
    }

    public boolean canGoForward() {
        return browser.getCefBrowser().canGoForward();
    }

    public String getUrl() {
        if (browser.getCefBrowser() != null) {
            return browser.getCefBrowser().getURL();
        }
        return "";
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

    public String getPageTitle() {
        return currentPageTitle;
    }

    private void fetchPageTitle() {
        // Title is obtained via CefDisplayHandler.onTitleChange, no JS needed
    }

    public void setPageTitle(String title) {
        if (title != null && !title.isEmpty()) {
            this.currentPageTitle = title;
            if (titleChangeListener != null) {
                titleChangeListener.onTitleChanged(title);
            }
        }
    }

    private void setupDarkModeSupport() {
        browser.getJBCefClient().addLoadHandler(new CefLoadHandler() {
            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                injectPagePadding();
                injectPopupInterceptor();
                applyDarkMode();
                notifyUrlChanged();
                fetchPageTitle();
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
            "    var el = e.target;" +
            "    while (el && el.tagName !== 'A') el = el.parentElement;" +
            "    if (el && el.tagName === 'A' && el.getAttribute('target') === '_blank') {" +
            "      e.preventDefault();" +
            "      window.location.href = el.href;" +
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

    private int brightnessLevel = 0;
    private String customTextColor = "#c0c0c0";

    public void setBrightnessLevel(int level) {
        this.brightnessLevel = level;
        applyBrightness();
    }

    public void setCustomTextColor(String color) {
        this.customTextColor = color;
        applyBrightness();
    }

    public String getCustomTextColor() {
        return customTextColor;
    }

    public int getBrightnessLevel() {
        return brightnessLevel;
    }

    private void applyDarkMode() {
        applyBrightness();
    }

    private void applyBrightness() {
        if (disposed) return;
        String filter;
        switch (brightnessLevel) {
            case 0:
                filter = isDarkTheme ? "invert(1) hue-rotate(180deg)" : "";
                break;
            case 1:
                filter = isDarkTheme ? "invert(1) hue-rotate(180deg) brightness(0.9)" : "brightness(0.9)";
                break;
            case 2:
                filter = isDarkTheme ? "invert(1) hue-rotate(180deg) brightness(0.8)" : "brightness(0.8)";
                break;
            case 3:
                filter = isDarkTheme ? "invert(1) hue-rotate(180deg) brightness(0.7)" : "brightness(0.7)";
                break;
            case -1:
                filter = isDarkTheme ? "invert(1) hue-rotate(180deg) brightness(1.1)" : "brightness(1.1)";
                break;
            case -2:
                filter = isDarkTheme ? "invert(1) hue-rotate(180deg) brightness(1.2)" : "brightness(1.2)";
                break;
            default:
                filter = "";
        }
        executeJavaScript("document.documentElement.style.filter = '" + filter + "';");
        injectDarkModeTextColor(isDarkTheme, customTextColor);
    }

    private void injectDarkModeTextColor(boolean dark, String color) {
        if (disposed) return;
        String script =
            "(function(isDark, textColor) {" +
            "  var id = '__webviewer_dark_text__';" +
            "  var existing = document.getElementById(id);" +
            "  if (isDark) {" +
            "    if (existing) existing.remove();" +
            "    var style = document.createElement('style');" +
            "    style.id = id;" +
            "    style.textContent = 'body, p, div, span, h1, h2, h3, h4, h5, h6, a, li, td, th, label, input, textarea { color: ' + textColor + ' !important; }';" +
            "    document.head.appendChild(style);" +
            "  } else {" +
            "    if (existing) existing.remove();" +
            "  }" +
            "})(" + dark + ", '" + color + "');";
        executeJavaScript(script);
    }

    private void executeJavaScript(String script) {
        if (disposed) return;
        UIUtil.invokeLaterIfNeeded(() -> {
            if (!disposed && browser.getCefBrowser() != null) {
                browser.getCefBrowser().executeJavaScript(script, browser.getCefBrowser().getURL(), 0);
            }
        });
    }

    @Override
    public void dispose() {
        disposed = true;
        Disposer.dispose(browser);
    }
}
