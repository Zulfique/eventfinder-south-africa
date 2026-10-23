// Fill Render env var values using the real UI controls.
async (page) => {
  const saJson = await page.evaluate(async () => {
    const r = await fetch('file:///C:/Users/27635/Downloads/Video%20Idea/.playwright-mcp/eventfinder-south-africa-firebase-adminsdk-fbsvc-a9a4cceb34.json');
    return (await r.text()).replace(/\r?\n\s*/g, '').trim();
  }).catch(async () => {
    // fall back: read via clipboard injection
    return null;
  });

  const results = [];

  async function setVar(key, value) {
    const row = page.locator('tr').filter({ has: page.locator(`input[value="${key}"]`) }).first();
    await row.waitFor({ state: 'visible', timeout: 15000 });

    const showBtn = row.locator('button[aria-label="Show secret"]');
    if (await showBtn.count()) {
      await showBtn.first().click();
      await page.waitForTimeout(300);
    }

    const ta = row.locator('textarea').first();
    await ta.waitFor({ state: 'visible', timeout: 10000 });
    await ta.fill(value, { timeout: 30000 });
    await page.keyboard.press('Tab');
    await page.waitForTimeout(400);

    const check = await ta.inputValue();
    results.push({ key, length: check.length, ok: check === value });
  }

  if (saJson) {
    try {
      await setVar('Firebase__ServiceAccountJson', saJson);
    } catch (e) {
      results.push({ key: 'Firebase__ServiceAccountJson', error: String(e) });
    }
  } else {
    results.push({ key: 'sa', error: 'could not read service account json' });
  }

  return JSON.stringify(results);
}
