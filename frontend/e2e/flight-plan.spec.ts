import { expect, test } from '@playwright/test';

test('search a flight, show its route and an alternate route', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Flight Plan Viewer' })).toBeVisible();

  await page.getByLabel('Search by callsign').fill('SIA200');
  const flight = page.getByRole('list', { name: 'Flights' }).getByRole('button', { name: /SIA200/ });
  await expect(flight).toBeVisible();
  await flight.click();

  const details = page.getByRole('region', { name: 'Route details' });
  await expect(details).toContainText('WSSS');
  // Leaflet draws routes as SVG paths in the overlay pane.
  await expect(page.locator('.leaflet-overlay-pane path').first()).toBeVisible();

  await page.getByRole('button', { name: 'Show alternate route' }).click();
  await expect(details).toContainText('Alternate:');
});

test('lists airways', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('tab', { name: /Airways/ }).click();
  await expect(page.getByRole('list', { name: 'Airways' }).getByRole('button').first()).toBeVisible();
});
