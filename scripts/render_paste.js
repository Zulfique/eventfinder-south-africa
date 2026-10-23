// Paste a value from the Windows clipboard into a Render env var textarea.
async (page) => {
  const results = [];

  async function setVarFromClipboard(key) {
    const row = page.locator('tr').filter({ has: page.locator(`input[value="${key}"]`) }).first();
    await row.waitFor({ state: 'visible', timeout: 20000 });

    const showBtn = row.locator('button[aria-label="Show secret"]');
    if (await showBtn.count()) {
      await showBtn.first().click();
      await page.waitForTimeout(400);
    }

    const ta = row.locator('textarea').first();
    await ta.waitFor({ state: 'visible', timeout: 15000 });
    await ta.click();
    await page.waitForTimeout(200);
    await ta.fill('');
    await page.waitForTimeout(200);

    // Ctrl+V paste
    await page.keyboard.press('Control+KeyV');
    await page.waitForTimeout(1200);

    const val = await ta.inputValue();
    results.push({ key, length: val.length, head: val.slice(0, 60) });
  }

  try {
    await setVarFromClipboard('Firebase__ServiceAccountJson');
  } catch (e) {
    results.push({ key: 'Firebase__ServiceAccountJson', error: String(e) });
  }

  return JSON.stringify(results);
}
