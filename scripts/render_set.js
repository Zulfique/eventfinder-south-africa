// Set a Render env var textarea value using React's native value setter,
// pulling the value from the Windows clipboard via Playwright clipboard permissions.
async (page) => {
  const results = [];

  await page.context().grantPermissions(['clipboard-read', 'clipboard-write'], {
    origin: 'https://dashboard.render.com'
  });

  async function setVar(key) {
    const value = await page.evaluate(() => navigator.clipboard.readText());
    if (!value) throw new Error('clipboard empty');

    const out = await page.evaluate(({ k, v }) => {
      const ki = Array.from(document.querySelectorAll('input[name*="envVarKey"]'))
        .find(i => i.value === k);
      if (!ki) return { error: 'key not found' };
      const tr = ki.closest('tr');
      const ta = tr.querySelector('textarea');
      if (!ta) return { error: 'textarea not found' };

      const proto = Object.getPrototypeOf(ta);
      const desc = Object.getOwnPropertyDescriptor(proto, 'value');
      desc.set.call(ta, v);
      ta.dispatchEvent(new Event('input', { bubbles: true }));
      ta.dispatchEvent(new Event('change', { bubbles: true }));
      return { length: ta.value.length, ok: ta.value === v, head: ta.value.slice(0, 50) };
    }, { k: key, v: value });

    await page.waitForTimeout(700);
    results.push({ key, ...out });
  }

  try {
    await setVar('Firebase__ServiceAccountJson');
  } catch (e) {
    results.push({ key: 'Firebase__ServiceAccountJson', error: String(e) });
  }

  return JSON.stringify(results);
}
