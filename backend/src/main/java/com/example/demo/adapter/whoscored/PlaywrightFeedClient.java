package com.example.demo.adapter.whoscored;

import com.example.demo.config.WhoScoredProperties;
import com.example.demo.exception.WhoScoredException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Transporte del feed con Chromium headless ({@code whoscored.client=browser}, default de la app).
 * Cloudflare bloquea al cliente HTTP de Java pero deja pasar a un navegador real (research R12): se navega una
 * vez a una página de WhoScored y cada consulta es un {@code fetch} hecho desde esa página.
 * <p>
 * El navegador se abre en la primera consulta de una ejecución y se cierra con {@link #close()} al terminarla.
 * Playwright no es thread-safe: se usa sólo desde el hilo del scheduler.
 */
public class PlaywrightFeedClient implements WhoScoredFeedClient {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightFeedClient.class);

    private static final String CHALLENGE_TITLE = "Just a moment";
    private static final long CHALLENGE_POLL_MILLIS = 500;

    /** fetch dentro de la página, cortado por AbortController a los {@code timeoutMs}. */
    private static final String FETCH_SCRIPT = """
            async ([url, timeoutMs]) => {
              const controller = new AbortController();
              const timer = setTimeout(() => controller.abort(), timeoutMs);
              try {
                const res = await fetch(url, {headers: {'Accept': 'application/json'}, signal: controller.signal});
                return {status: res.status, body: await res.text(), aborted: false};
              } catch (e) {
                return {status: 0, body: String(e), aborted: e.name === 'AbortError'};
              } finally {
                clearTimeout(timer);
              }
            }""";

    private final WhoScoredProperties properties;
    // Recursos abiertos durante una ejecución; se cierran explícitamente en close() (en orden inverso).
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    public PlaywrightFeedClient(WhoScoredProperties properties) {
        this.properties = properties;
    }

    @Override
    public synchronized FeedResponse get(String pathAndQuery) {
        Page current = openIfNeeded();
        Object result;
        try {
            // El primer fetch compite con los scripts de la página (~20 s medidos), así que el límite es el de la
            // navegación y no el read-timeout HTTP. Playwright no serializa Long hacia JS: va como int.
            int timeoutMillis = (int) Math.min(Integer.MAX_VALUE, properties.browser().navigationTimeout().toMillis());
            result = current.evaluate(FETCH_SCRIPT, List.of(pathAndQuery, timeoutMillis));
        } catch (PlaywrightException ex) {
            close();
            throw new WhoScoredException(WhoScoredException.BROWSER_ERROR, "El navegador no pudo consultar WhoScored", ex);
        }
        if (!(result instanceof Map<?, ?> response) || !(response.get("status") instanceof Number status)) {
            throw new WhoScoredException(WhoScoredException.BROWSER_ERROR, "Respuesta inesperada del navegador");
        }
        if (Boolean.TRUE.equals(response.get("aborted"))) {
            throw new WhoScoredException(WhoScoredException.TIMEOUT, "WhoScored no respondió a tiempo");
        }
        if (status.intValue() == 0) {
            throw new WhoScoredException(WhoScoredException.BROWSER_ERROR,
                    "El fetch del navegador falló: " + response.get("body"));
        }
        return new FeedResponse(status.intValue(), String.valueOf(response.get("body")));
    }

    @Override
    public synchronized void close() {
        page = null;
        if (context != null) {
            closeQuietly(context::close);
            context = null;
        }
        if (browser != null) {
            closeQuietly(browser::close);
            browser = null;
        }
        if (playwright != null) {
            closeQuietly(playwright::close);
            playwright = null;
        }
    }

    private static void closeQuietly(Runnable closeAction) {
        try {
            closeAction.run();
        } catch (PlaywrightException ex) {
            log.warn("whoscored_browser_close_failed");
        }
    }

    private Page openIfNeeded() {
        if (page != null) {
            return page;
        }
        try {
            // La app no descarga navegadores: Chromium se instala aparte con el CLI de Playwright.
            playwright = Playwright.create(new Playwright.CreateOptions()
                    .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(List.of("--disable-blink-features=AutomationControlled")));
            context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(properties.userAgent())
                    .setLocale("en-US"));
            Page opened = context.newPage();
            long timeoutMillis = properties.browser().navigationTimeout().toMillis();
            // La página tiene publicidad que nunca termina de cargar: alcanza con el HTML inicial.
            opened.navigate(landingUrl(), new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(timeoutMillis));
            boolean passed = waitForChallenge(opened, timeoutMillis);
            // Si el challenge no se resolvió, las consultas devolverán 403 y el adapter lo trata como bloqueo.
            log.info("whoscored_browser_ready challengePassed={}", passed);
            page = opened;
            return page;
        } catch (PlaywrightException ex) {
            close();
            throw new WhoScoredException(WhoScoredException.BROWSER_ERROR,
                    "No se pudo abrir WhoScored en el navegador (Chromium no instalado o sitio inaccesible)", ex);
        }
    }

    private static boolean waitForChallenge(Page opened, long timeoutMillis) {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000;
        while (opened.title().contains(CHALLENGE_TITLE)) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            opened.waitForTimeout(CHALLENGE_POLL_MILLIS);
        }
        return true;
    }

    private String landingUrl() {
        String base = properties.baseUrl().toString();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + properties.browser().landingPath();
    }
}
