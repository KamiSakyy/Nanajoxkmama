/**
 * AniBeat — reference screenshots.
 *
 * Renders the real site (built `web/dist`) in Chromium at exact phone and
 * desktop viewports and walks through every screen so the Android build can be
 * verified against the original pixel by pixel.
 *
 * Output: docs/screenshots/*.png + docs/screenshots/capture-report.txt
 */
import { chromium } from "playwright";
import { mkdir, writeFile } from "node:fs/promises";

const BASE = process.env.BASE_URL || "http://127.0.0.1:4173/";
const OUT = process.env.OUT_DIR || "docs/screenshots";
const MOBILE = { width: 390, height: 844 };
const ANDROID_UA =
  "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36";

const report = [];
const problems = [];
const log = (msg) => {
  const line = `[${new Date().toISOString()}] ${msg}`;
  console.log(line);
  report.push(line);
  if (/pageerror|console\.error|FAIL|failed|Нет соединения|не найдена/.test(msg)) problems.push(line);
};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function apiJson(url) {
  try {
    const res = await fetch(url);
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  }
}

const ANIME_FALLBACKS = ["one-piece", "naruto", "jujutsu-kaisen", "attack-on-titan"];

async function main() {
  await mkdir(OUT, { recursive: true });

  const animeJson = await apiJson("https://api.animethemes.moe/anime?page[size]=12&sort=random&include[animethemes]=1");
  const slugs = (animeJson?.anime ?? []).map((a) => a.slug);
  const candidates = [...slugs, ...ANIME_FALLBACKS];
  log(`anime slug candidates: ${candidates.slice(0, 5).join(", ")}...`);

  const browser = await chromium.launch({ args: ["--no-sandbox", "--autoplay-policy=no-user-gesture-required"] });

  /* ------------------------- mobile (phone) ------------------------- */
  const phone = await browser.newContext({
    viewport: MOBILE,
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
    locale: "ru-RU",
    colorScheme: "dark",
    userAgent: ANDROID_UA,
    reducedMotion: "no-preference",
  });
  const page = await phone.newPage();
  page.on("console", (m) => {
    if (m.type() === "error") log(`console.error: ${m.text()}`);
  });
  page.on("pageerror", (e) => log(`pageerror: ${e.message}`));
  page.on("requestfailed", (r) => {
    const url = r.url();
    // CORS-blocked poster thumbnails are expected on a plain static server and
    // do not happen inside the app (native origin serves the same content).
    if (url.includes("anilistcdn") || url.includes("r2.dev")) return;
    if (r.failure()?.errorText === "net::ERR_ABORTED") return;
    log(`requestfailed: ${url} ${r.failure()?.errorText}`);
  });

  const shot = async (name, opts = {}) => {
    await page.screenshot({ path: `${OUT}/${name}.png`, animations: "disabled", ...opts });
    log(`shot ${name}`);
  };
  const tapLabel = async (label) => {
    const el = page.locator(`button[aria-label="${label}"]`).first();
    await el.waitFor({ state: "visible", timeout: 20000 });
    await el.click({ timeout: 20000 });
    log(`tap [${label}]`);
  };
  const scrollTo = async (y) => {
    await page.evaluate((v) => window.scrollTo({ top: v }), y);
    await sleep(1200);
  };
  const sheetScroll = async () => {
    await page.evaluate(() => {
      const sc = document.querySelector("[data-sheet-scroll]");
      if (sc) sc.scrollTop = sc.scrollHeight;
    });
    await sleep(900);
  };

  /* 1. Главная */
  await page.goto(BASE, { waitUntil: "domcontentloaded", timeout: 60000 });
  await sleep(10000);
  await shot("mobile-01-home");
  await scrollTo(620);
  await shot("mobile-02-home-sections");
  await scrollTo(1500);
  await shot("mobile-03-home-more");
  await scrollTo(0);
  await sleep(500);

  /* 2. Поиск */
  await page.goto(`${BASE}#/search`, { waitUntil: "domcontentloaded" });
  await sleep(2500);
  await shot("mobile-04-search-empty");
  const input = page.locator('input[placeholder="Аниме, песня, исполнитель"]').first();
  await input.click();
  await input.type("naruto", { delay: 60 });
  await sleep(7000);
  await shot("mobile-05-search-results");
  await scrollTo(700);
  await shot("mobile-06-search-results-scrolled");

  /* 3. Обзор */
  await page.goto(`${BASE}#/browse`, { waitUntil: "domcontentloaded" });
  await sleep(8000);
  await shot("mobile-07-browse-years");
  await scrollTo(900);
  await shot("mobile-08-browse-years-scrolled");
  for (const [tabName, file] of [
    ["Новое", "mobile-09-browse-fresh"],
    [/^OP/, "mobile-10-browse-op"],
  ]) {
    const tab = page.getByRole("tab", { name: tabName }).first();
    if (await tab.count()) {
      await tab.click();
      await sleep(7000);
      await shot(file);
    }
  }
  await page.goto(`${BASE}#/year/2024`, { waitUntil: "domcontentloaded" });
  await sleep(8000);
  await shot("mobile-11-year-2024");

  /* 4. Страница аниме */
  let animeOk = false;
  for (const slug of candidates.slice(0, 6)) {
    await page.goto(`${BASE}#/anime/${slug}`, { waitUntil: "domcontentloaded" });
    await sleep(7000);
    const broken = await page.getByText("Нет соединения").count();
    if (broken === 0) {
      await shot("mobile-12-anime-detail");
      await scrollTo(760);
      await shot("mobile-13-anime-tracks");
      log(`anime page ok: ${slug}`);
      animeOk = true;
      break;
    }
    log(`anime page failed for slug: ${slug}`);
  }
  if (!animeOk) log("FAIL: no anime detail page rendered");

  /* 5. Плеер + очередь + видео + мини-плеер */
  await page.goto(BASE, { waitUntil: "domcontentloaded" });
  await sleep(9000);
  try {
    const listen = page.getByRole("button", { name: "Слушать" }).first();
    await listen.waitFor({ state: "visible", timeout: 25000 });
    await listen.click();
    await sleep(9000);
    await shot("mobile-17-now-playing");

    const queue = page.getByRole("button", { name: "Очередь" }).first();
    if (await queue.count()) {
      await queue.click();
      await sleep(1800);
      await shot("mobile-18-queue-sheet");
      await page.keyboard.press("Escape");
      await sleep(1000);
    }

    const video = page.getByRole("button", { name: "Видео" }).first();
    if (await video.count()) {
      await video.click();
      await sleep(6000);
      await shot("mobile-19-now-playing-video");
      await page.keyboard.press("Escape");
      await sleep(800);
    }

    await tapLabel("Свернуть");
    await sleep(2500);
    await shot("mobile-20-mini-player");

    // меню трека из мини-плеера
    const np = page.locator('[role="button"][aria-label="Открыть плеер"]').first();
    if (await np.count()) {
      await np.click();
      await sleep(2500);
      await tapLabel("Ещё");
      await sleep(1800);
      await shot("mobile-21-track-menu");
      await page.keyboard.press("Escape");
      await sleep(900);
    }
  } catch (e) {
    log(`FAIL playback flow: ${e.message}`);
  }

  /* 6. Медиатека */
  await page.goto(`${BASE}#/library`, { waitUntil: "domcontentloaded" });
  await sleep(3000);
  await shot("mobile-15-library");
  for (const [tabName, file] of [
    ["Скачано", "mobile-16a-library-downloads"],
    ["Плейлисты", "mobile-16b-library-playlists"],
    ["История", "mobile-16c-library-history"],
  ]) {
    const tab = page.getByRole("tab", { name: tabName }).first();
    if (await tab.count()) {
      await tab.click();
      await sleep(1500);
      await shot(file);
    }
  }

  /* 7. Настройки */
  await page.goto(BASE, { waitUntil: "domcontentloaded" });
  await sleep(7000);
  try {
    await tapLabel("Настройки");
    await sleep(1800);
    await shot("mobile-22-settings");
    await sheetScroll();
    await shot("mobile-23-settings-scrolled");
  } catch (e) {
    log(`FAIL settings: ${e.message}`);
  }

  await phone.close();

  /* ------------------------- desktop ------------------------- */
  const desktop = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
    locale: "ru-RU",
    colorScheme: "dark",
  });
  const d = await desktop.newPage();
  await d.goto(BASE, { waitUntil: "domcontentloaded" });
  await sleep(10000);
  await d.screenshot({ path: `${OUT}/desktop-01-home.png`, animations: "disabled" });
  log("shot desktop-01-home");
  await d.goto(`${BASE}#/browse`, { waitUntil: "domcontentloaded" });
  await sleep(8000);
  await d.screenshot({ path: `${OUT}/desktop-02-browse.png`, animations: "disabled" });
  log("shot desktop-02-browse");
  await desktop.close();

  await browser.close();
  log(problems.length ? `PROBLEMS (${problems.length}):\n${problems.join("\n")}` : "no console errors, no failed screens");
  await writeFile(`${OUT}/capture-report.txt`, report.join("\n") + "\n", "utf8");
  log("done");
}

main().catch(async (e) => {
  log(`FATAL: ${e?.stack || e}`);
  try {
    await mkdir(OUT, { recursive: true });
    await writeFile(`${OUT}/capture-report.txt`, report.join("\n") + "\n", "utf8");
  } catch {}
  process.exit(1);
});
