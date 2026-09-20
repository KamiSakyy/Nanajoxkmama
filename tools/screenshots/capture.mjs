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
const log = (msg) => {
  const line = `[${new Date().toISOString()}] ${msg}`;
  console.log(line);
  report.push(line);
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

async function main() {
  await mkdir(OUT, { recursive: true });

  // A random anime + artist slug so the detail pages show real content.
  const animeJson = await apiJson("https://api.animethemes.moe/anime?page[size]=1&sort=random&include[animethemes]=1");
  const artistJson = await apiJson("https://api.animethemes.moe/artist?page[size]=1&sort=random");
  const animeSlug = animeJson?.anime?.[0]?.slug ?? "one-piece";
  const artistSlug = artistJson?.artists?.[0]?.slug ?? null;
  log(`anime slug: ${animeSlug}, artist slug: ${artistSlug}`);

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
  page.on("requestfailed", (r) => log(`requestfailed: ${r.url()} ${r.failure()?.errorText}`));

  const shot = async (name, opts = {}) => {
    await page.screenshot({ path: `${OUT}/${name}.png`, animations: "disabled", ...opts });
    log(`shot ${name}`);
  };
  const tapText = async (text, timeout = 15000) => {
    const el = page.getByText(text, { exact: false }).first();
    await el.waitFor({ state: "visible", timeout });
    await el.click({ timeout });
    log(`tap "${text}"`);
  };

  // 1. Home
  await page.goto(BASE, { waitUntil: "domcontentloaded", timeout: 60000 });
  await sleep(9000);
  await shot("mobile-01-home");
  await page.evaluate(() => window.scrollTo({ top: 620 }));
  await sleep(1500);
  await shot("mobile-02-home-sections");
  await page.evaluate(() => window.scrollTo({ top: 1500 }));
  await sleep(1500);
  await shot("mobile-03-home-more");
  await page.evaluate(() => window.scrollTo({ top: 0 }));
  await sleep(600);

  // 2. Search
  await page.goto(`${BASE}#/search`, { waitUntil: "domcontentloaded" });
  await sleep(2500);
  await shot("mobile-04-search-empty");
  const input = page.locator('input[placeholder="Аниме, песня, исполнитель"]').first();
  await input.click();
  await input.type("naruto", { delay: 60 });
  await sleep(6000);
  await shot("mobile-05-search-results");
  await page.evaluate(() => window.scrollTo({ top: 700 }));
  await sleep(1200);
  await shot("mobile-06-search-results-scrolled");

  // 3. Browse
  await page.goto(`${BASE}#/browse`, { waitUntil: "domcontentloaded" });
  await sleep(7000);
  await shot("mobile-07-browse-years");
  await page.evaluate(() => window.scrollTo({ top: 800 }));
  await sleep(1200);
  await shot("mobile-08-browse-years-scrolled");
  const freshTab = page.getByRole("tab", { name: "Новое" }).first();
  if (await freshTab.count()) {
    await freshTab.click();
    await sleep(6000);
    await shot("mobile-09-browse-fresh");
  }
  const opTab = page.getByRole("tab", { name: /^OP/ }).first();
  if (await opTab.count()) {
    await opTab.click();
    await sleep(5000);
    await shot("mobile-10-browse-op");
  }
  await page.goto(`${BASE}#/year/2024`, { waitUntil: "domcontentloaded" });
  await sleep(7000);
  await shot("mobile-11-year-2024");

  // 4. Anime detail
  await page.goto(`${BASE}#/anime/${animeSlug}`, { waitUntil: "domcontentloaded" });
  await sleep(8000);
  await shot("mobile-12-anime-detail");
  await page.evaluate(() => window.scrollTo({ top: 700 }));
  await sleep(1500);
  await shot("mobile-13-anime-tracks");

  // 5. Artist detail
  if (artistSlug) {
    await page.goto(`${BASE}#/artist/${artistSlug}`, { waitUntil: "domcontentloaded" });
    await sleep(7000);
    await shot("mobile-14-artist-detail");
  }

  // 6. Library
  await page.goto(`${BASE}#/library`, { waitUntil: "domcontentloaded" });
  await sleep(2500);
  await shot("mobile-15-library");
  const tabs = ["Плейлисты", "История", "Загрузки"];
  for (const t of tabs) {
    const tab = page.getByRole("tab", { name: new RegExp(t) }).first();
    if (await tab.count()) {
      await tab.click();
      await sleep(1200);
      await shot(`mobile-16-library-${t.toLowerCase()}`);
    }
  }

  // 7. Playback: hero "Слушать" opens Now Playing
  await page.goto(BASE, { waitUntil: "domcontentloaded" });
  await sleep(8000);
  try {
    await tapText("Слушать", 20000);
    await sleep(7000);
    await shot("mobile-17-now-playing");
    // queue sheet
    const queueBtn = page.getByText("Очередь", { exact: true }).first();
    if (await queueBtn.count()) {
      await queueBtn.click();
      await sleep(1600);
      await shot("mobile-18-queue-sheet");
      await page.keyboard.press("Escape");
      await sleep(900);
    }
    // video mode
    const videoBtn = page.getByText("Видео", { exact: true }).first();
    if (await videoBtn.count()) {
      await videoBtn.click();
      await sleep(4000);
      await shot("mobile-19-now-playing-video");
      await page.keyboard.press("Escape");
      await sleep(600);
    }
    // minimize -> mini player
    const collapse = page.locator('button[aria-label="Свернуть"], button:has-text("Свернуть")').first();
    if (await collapse.count()) {
      await collapse.click();
      await sleep(1400);
      await shot("mobile-20-mini-player");
    }
  } catch (e) {
    log(`playback flow failed: ${e.message}`);
  }

  // 8. Track menu sheet
  try {
    await page.goto(BASE, { waitUntil: "domcontentloaded" });
    await sleep(7000);
    await page.evaluate(() => window.scrollTo({ top: 1500 }));
    await sleep(1500);
    const more = page.locator('button[aria-label="Ещё"]').first();
    if (await more.count()) {
      await more.click();
      await sleep(1500);
      await shot("mobile-21-track-menu");
      await page.keyboard.press("Escape");
      await sleep(900);
    }
  } catch (e) {
    log(`track menu failed: ${e.message}`);
  }

  // 9. Settings sheet
  try {
    await page.goto(BASE, { waitUntil: "domcontentloaded" });
    await sleep(6000);
    const settings = page.locator('button[aria-label="Настройки"]').first();
    if (await settings.count()) {
      await settings.click();
      await sleep(1600);
      await shot("mobile-22-settings");
      await page.evaluate(() => {
        const sc = document.querySelector("[data-sheet-scroll]");
        if (sc) sc.scrollTop = sc.scrollHeight;
      });
      await sleep(1000);
      await shot("mobile-23-settings-scrolled");
    }
  } catch (e) {
    log(`settings failed: ${e.message}`);
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
  await sleep(9000);
  await d.screenshot({ path: `${OUT}/desktop-01-home.png`, animations: "disabled" });
  log("shot desktop-01-home");
  await d.goto(`${BASE}#/browse`, { waitUntil: "domcontentloaded" });
  await sleep(7000);
  await d.screenshot({ path: `${OUT}/desktop-02-browse.png`, animations: "disabled" });
  log("shot desktop-02-browse");
  await desktop.close();

  await browser.close();
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
