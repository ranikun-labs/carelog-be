import { createRequire } from 'node:module';
import { randomUUID } from 'node:crypto';
import { writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const frontendRoot = process.env.CARELOG_FE_ROOT;
const gatewaySecret = process.env.CARELOG_RPL64_TEST_GATEWAY_SECRET;
const resultFile = process.env.CARELOG_RPL64_CUSTOMER_RESULT_FILE;

if (!frontendRoot || !gatewaySecret || !resultFile) {
  throw new Error('The RPL-64 browser test is missing its test-only runtime inputs.');
}

const requireFromFrontend = createRequire(resolve(frontendRoot, 'package.json'));
const { expect, test } = requireFromFrontend('@playwright/test');

test('the FE production Customer adapter reaches BE HTTP and reads back its scoped record', async ({ page }) => {
  const organizationId = randomUUID();
  const otherOrganizationId = randomUUID();
  const accountId = randomUUID();
  const otherAccountId = randomUUID();
  const publicId = randomUUID();
  const otherPublicId = randomUUID();
  const displayName = 'RPL-64 FE HTTP ' + randomUUID();
  const customerMemo = 'rpl64-disposable-http-postgres-readback';

  await page.goto('/');
  await page.locator('#root > *').first().waitFor({ state: 'visible' });

  const result = await page.evaluate(async (input) => {
    const productionModuleUrl = '/src/integrations/carelog/productionComposition.ts';
    const productionModule = await import(productionModuleUrl);

    // Mirrors the existing BE test-only trusted-header fixture; it does not exercise Gateway or Identity.
    const createSyntheticFetch = (organization, account, publicIdentity) => async (request, options = {}) => {
      const headers = new Headers(options.headers);
      headers.set('X-Gateway-Secret', input.gatewaySecret);
      headers.set('X-User-Id', account);
      headers.set('X-Organization-Id', organization);
      headers.set('X-Role', 'MANAGER');
      headers.set('X-Public-Id', publicIdentity);
      return window.fetch(request, { ...options, headers });
    };

    const createPorts = (organization, account, publicIdentity) =>
      productionModule.createProductionProductPorts({
        apiBaseUrl: window.location.origin,
        sessionProvider: { credentials: 'omit' },
        fetchImpl: createSyntheticFetch(organization, account, publicIdentity),
      });

    const organizationPorts = createPorts(input.organizationId, input.accountId, input.publicId);
    const created = await organizationPorts.customerPort.create({
      displayName: input.displayName,
      customerMemo: input.customerMemo,
    });
    const organizationRows = await organizationPorts.customerPort.list();
    const listed = organizationRows.find((row) => row.id === created.id);
    if (!listed) throw new Error('The FE CustomerPort could not read its created record back over HTTP.');

    const otherOrganizationPorts = createPorts(
      input.otherOrganizationId,
      input.otherAccountId,
      input.otherPublicId,
    );
    const otherOrganizationRows = await otherOrganizationPorts.customerPort.list();

    return {
      id: created.id,
      organizationId: input.organizationId,
      displayName: created.displayName,
      customerMemo: created.customerMemo,
      listedDisplayName: listed.displayName,
      visibleInOtherOrganization: otherOrganizationRows.some((row) => row.id === created.id),
    };
  }, {
    gatewaySecret,
    organizationId,
    otherOrganizationId,
    accountId,
    otherAccountId,
    publicId,
    otherPublicId,
    displayName,
    customerMemo,
  });

  expect(result.displayName).toBe(displayName);
  expect(result.customerMemo).toBe(customerMemo);
  expect(result.listedDisplayName).toBe(displayName);
  expect(result.visibleInOtherOrganization).toBe(false);

  await writeFile(resultFile, JSON.stringify(result), { encoding: 'utf8', flag: 'wx' });
});
