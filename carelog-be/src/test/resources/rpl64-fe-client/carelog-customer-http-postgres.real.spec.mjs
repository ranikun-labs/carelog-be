import { createRequire } from 'node:module';
import { randomUUID } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const frontendRoot = process.env.CARELOG_FE_ROOT;
const gatewaySecret = process.env.CARELOG_RPL64_TEST_GATEWAY_SECRET;
const resultFile = process.env.CARELOG_RPL64_CUSTOMER_RESULT_FILE;
if (!frontendRoot || !gatewaySecret || !resultFile) {
  throw new Error('The RPL-64 browser test is missing its test-only runtime inputs.');
}

const requireFromFrontend = createRequire(resolve(frontendRoot, 'package.json'));
const { expect, test } = requireFromFrontend('@playwright/test');
test.use({ timezoneId: 'Asia/Seoul' });

test('the FE production Customer and CustomerEvent adapters reach BE HTTP with scoped readback', async ({ page }) => {
  const organizationId = randomUUID();
  const otherOrganizationId = randomUUID();
  const accountId = randomUUID();
  const otherAccountId = randomUUID();
  const publicId = randomUUID();
  const otherPublicId = randomUUID();
  const displayName = 'RPL-64 FE HTTP ' + randomUUID();
  const customerMemo = 'rpl64-disposable-http-postgres-readback';
  const updatedDisplayName = 'RPL-64 FE HTTP edited ' + randomUUID();
  const updatedCustomerMemo = 'rpl64-disposable-http-postgres-edited';
  const eventScheduledAt = '2027-01-12T10:00:00+09:00';
  const eventRescheduledAt = '2027-01-12T10:15:00+09:00';
  const occurredEventScheduledAt = '2027-01-13T10:00:00+09:00';
  const occurredAt = '2027-01-13T10:30:00+09:00';
  const eventListFrom = '2027-01-01T00:00:00+09:00';
  const eventListTo = '2027-02-01T00:00:00+09:00';

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
    const detailedCustomer = await organizationPorts.customerPort.get(created.id);
    const editedCustomer = await organizationPorts.customerPort.edit(created.id, detailedCustomer, {
      displayName: input.updatedDisplayName,
      customerMemo: input.updatedCustomerMemo,
    });
    const editedCustomerRows = await organizationPorts.customerPort.list();
    const listedEditedCustomer = editedCustomerRows.find((row) => row.id === created.id);
    if (!listedEditedCustomer) throw new Error('The FE CustomerPort could not list its edited record.');

    const createdEvent = await organizationPorts.customerEventPort.create({
      status: 'PLANNED',
      customerId: created.id,
      scheduledAt: input.eventScheduledAt,
      descriptor: '상담',
      note: 'event initial note',
    });
    const eventRows = await organizationPorts.customerEventPort.list({
      customerId: created.id,
      limit: 50,
    });
    const listedEvent = eventRows.find((row) => row.id === createdEvent.id);
    if (!listedEvent) throw new Error('The FE CustomerEventPort could not list its created event.');

    const detailedEvent = await organizationPorts.customerEventPort.get(createdEvent.id);
    const editedEvent = await organizationPorts.customerEventPort.edit(detailedEvent, {
      scheduledAt: input.eventRescheduledAt,
      note: 'event updated note',
    });
    const canceledEvent = await organizationPorts.customerEventPort.cancel(createdEvent.id);
    const createdOccurredEvent = await organizationPorts.customerEventPort.create({
      status: 'PLANNED',
      customerId: created.id,
      scheduledAt: input.occurredEventScheduledAt,
      descriptor: '상담',
      note: 'event occur initial note',
    });
    const occurredEvent = await organizationPorts.customerEventPort.occur(
      createdOccurredEvent.id,
      input.occurredAt,
    );

    const otherOrganizationPorts = createPorts(
      input.otherOrganizationId,
      input.otherAccountId,
      input.otherPublicId,
    );
    const otherOrganizationRows = await otherOrganizationPorts.customerPort.list();
    const otherOrganizationEvents = await otherOrganizationPorts.customerEventPort.list({
      from: input.eventListFrom,
      to: input.eventListTo,
      limit: 100,
    });

    return {
      id: created.id,
      organizationId: input.organizationId,
      createdDisplayName: created.displayName,
      createdCustomerMemo: created.customerMemo,
      customerDetailDisplayName: detailedCustomer.displayName,
      customerDetailMemo: detailedCustomer.customerMemo,
      displayName: editedCustomer.displayName,
      customerMemo: editedCustomer.customerMemo,
      createdListedDisplayName: listed.displayName,
      listedDisplayName: listedEditedCustomer.displayName,
      listedCustomerMemo: listedEditedCustomer.customerMemo,
      visibleInOtherOrganization: otherOrganizationRows.some((row) => row.id === created.id),
      eventId: createdEvent.id,
      eventCustomerId: createdEvent.customerId,
      eventCreatedStatus: createdEvent.status,
      eventListedId: listedEvent.id,
      eventDetailId: detailedEvent.id,
      eventEditedScheduledAt: editedEvent.scheduledAt,
      eventEditedNote: editedEvent.note,
      eventCancelledStatus: canceledEvent.status,
      eventCancelledScheduledAt: canceledEvent.scheduledAt,
      eventCancelledHasOccurredAt: Object.prototype.hasOwnProperty.call(canceledEvent, 'occurredAt'),
      eventVisibleInOtherOrganization: otherOrganizationEvents.some((row) => row.id === createdEvent.id),
      occurredEventId: occurredEvent.id,
      occurredEventCustomerId: occurredEvent.customerId,
      occurredEventStatus: occurredEvent.status,
      occurredEventOccurredAt: occurredEvent.occurredAt,
      occurredEventScheduledAt: occurredEvent.scheduledAt,
      occurredEventVisibleInOtherOrganization: otherOrganizationEvents.some(
        (row) => row.id === occurredEvent.id,
      ),
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
    updatedDisplayName,
    updatedCustomerMemo,
    eventScheduledAt,
    eventRescheduledAt,
    occurredEventScheduledAt,
    occurredAt,
    eventListFrom,
    eventListTo,
  });

  expect(result.createdDisplayName).toBe(displayName);
  expect(result.createdCustomerMemo).toBe(customerMemo);
  expect(result.customerDetailDisplayName).toBe(displayName);
  expect(result.customerDetailMemo).toBe(customerMemo);
  expect(result.displayName).toBe(updatedDisplayName);
  expect(result.customerMemo).toBe(updatedCustomerMemo);
  expect(result.createdListedDisplayName).toBe(displayName);
  expect(result.listedDisplayName).toBe(updatedDisplayName);
  expect(result.listedCustomerMemo).toBe(updatedCustomerMemo);
  expect(result.visibleInOtherOrganization).toBe(false);
  expect(result.eventCustomerId).toBe(result.id);
  expect(result.eventCreatedStatus).toBe('PLANNED');
  expect(result.eventListedId).toBe(result.eventId);
  expect(result.eventDetailId).toBe(result.eventId);
  expect(Date.parse(result.eventEditedScheduledAt)).toBe(Date.parse(eventRescheduledAt));
  expect(result.eventEditedNote).toBe('event updated note');
  expect(result.eventCancelledStatus).toBe('CANCELLED');
  expect(Date.parse(result.eventCancelledScheduledAt)).toBe(Date.parse(eventRescheduledAt));
  expect(Date.parse(result.eventCancelledScheduledAt)).toBe(Date.parse(result.eventEditedScheduledAt));
  expect(result.eventCancelledHasOccurredAt).toBe(false);
  expect(result.eventVisibleInOtherOrganization).toBe(false);
  expect(result.occurredEventCustomerId).toBe(result.id);
  expect(result.occurredEventStatus).toBe('OCCURRED');
  expect(Date.parse(result.occurredEventOccurredAt)).toBe(Date.parse(occurredAt));
  expect(Date.parse(result.occurredEventScheduledAt)).toBe(Date.parse(occurredEventScheduledAt));
  expect(result.occurredEventVisibleInOtherOrganization).toBe(false);

  await writeFile(resultFile, JSON.stringify(result), { encoding: 'utf8', flag: 'wx' });
});



function rpl64WaitForApiResponse(page, method, pathname) {
  return page.waitForResponse((response) => {
    const url = new URL(response.url());
    return response.request().method() === method && url.pathname === pathname;
  });
}

async function rpl64ResponseData(response, label) {
  const payload = await response.json();
  if (
    !payload ||
    typeof payload.status !== 'number' ||
    payload.status !== response.status() ||
    typeof payload.message !== 'string' ||
    !Object.prototype.hasOwnProperty.call(payload, 'data')
  ) {
    throw new Error(label + ' response envelope is invalid.');
  }
  return payload.data;
}

async function rpl64LoginThroughUi(page, account, password) {
  await page.locator('[data-auth-form] input[name="account"]').fill(account);
  await page.locator('[data-auth-form] input[name="secret"]').fill(password);
  const responsePromise = rpl64WaitForApiResponse(page, 'POST', '/api/v1/auth/login');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  await expect(page.locator('[data-app-shell]')).toBeVisible();
}

function rpl64RegisterSyntheticAuthority(page, authority) {
  return page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    if (request.method() === 'POST' && pathname === '/api/v1/auth/login') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 200,
          message: 'OK',
          data: {
            accessToken: 'rpl64-synthetic-access-token',
            refreshToken: 'rpl64-synthetic-refresh-token',
          },
        }),
      });
      return;
    }
    if (request.method() === 'POST' && pathname === '/api/v1/auth/logout') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ status: 200, message: 'OK', data: null }),
      });
      return;
    }
    const headers = { ...request.headers() };
    for (const headerName of Object.keys(headers)) {
      if (headerName.toLowerCase() === 'authorization') delete headers[headerName];
    }
    Object.assign(headers, {
      'x-gateway-secret': gatewaySecret,
      'x-user-id': authority.accountId,
      'x-organization-id': authority.organizationId,
      'x-role': 'MANAGER',
      'x-public-id': authority.publicId,
    });
    await route.continue({ headers });
  });
}

function rpl64SeoulDateTimeLocalAfterMinutes(offsetMinutes) {
  const values = Object.fromEntries(
    new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Seoul',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(new Date(Date.now() + offsetMinutes * 60 * 1000))
      .map(({ type, value }) => [type, value]),
  );
  const { year, month, day, hour, minute } = values;
  if (!year || !month || !day || !hour || !minute) {
    throw new Error('Could not create the required Asia/Seoul date and time.');
  }
  return year + '-' + month + '-' + day + 'T' + hour + ':' + minute;
}

async function rpl64OpenCustomerDetail(page, customerId, displayName) {
  await page.locator('[data-side-navigation] a[href="/app/customers"]').click();
  await expect(page.locator('[data-customers-page]')).toBeVisible();
  const customerCard = page
    .locator('[data-customer-card][data-customer-id="' + customerId + '"]')
    .filter({ hasText: displayName });
  await expect(customerCard).toHaveCount(1);
  await customerCard.locator('a').click();
  await expect(page.locator('[data-customer-detail-page]')).toHaveAttribute(
    'data-selected-customer-id',
    customerId,
  );
}

test('Carelog Customer and CustomerEvent screens use real BE HTTP with synthetic test authority', async ({ page, browser }) => {
  test.setTimeout(120_000);
  const organizationId = randomUUID();
  const otherOrganizationId = randomUUID();
  const accountId = randomUUID();
  const otherAccountId = randomUUID();
  const publicId = randomUUID();
  const otherPublicId = randomUUID();
  const displayName = 'RPL-64 UI Customer ' + randomUUID();
  const customerMemo = 'rpl64-ui-customer-memo';
  const updatedDisplayName = 'RPL-64 UI Customer Edited ' + randomUUID();
  const updatedCustomerMemo = 'rpl64-ui-customer-memo-edited';
  const descriptor = 'RPL-64 UI event ' + randomUUID();
  const scheduledAt = rpl64SeoulDateTimeLocalAfterMinutes(24 * 60);
  const rescheduledAt = rpl64SeoulDateTimeLocalAfterMinutes(48 * 60);
  const occurredAt = rpl64SeoulDateTimeLocalAfterMinutes(-1);

  await rpl64RegisterSyntheticAuthority(page, { organizationId, accountId, publicId });
  await page.goto('/auth/login');
  await rpl64LoginThroughUi(page, 'rpl64-synthetic-manager', 'synthetic-test-only');
  await page.locator('[data-side-navigation] a[href="/app/customers"]').click();
  await expect(page.locator('[data-customers-page]')).toBeVisible();

  const createCustomerCta = page.locator('[data-first-customer-cta], [data-customer-add-cta]');
  await expect(createCustomerCta).toHaveCount(1);
  await createCustomerCta.click();
  await expect(page.locator('[data-customer-create-page]')).toBeVisible();
  await page.locator('[data-customer-field="displayName"]').fill(displayName);
  await page.locator('[data-customer-field="customerMemo"]').fill(customerMemo);
  const customerCreatePromise = rpl64WaitForApiResponse(page, 'POST', '/api/v1/users/customers');
  await page.locator('[data-customer-form-submit]').click();
  const customerCreateResponse = await customerCreatePromise;
  expect(customerCreateResponse.status()).toBeGreaterThanOrEqual(200);
  expect(customerCreateResponse.status()).toBeLessThan(300);
  const createdCustomer = await rpl64ResponseData(customerCreateResponse, 'Customer');
  const customerId = createdCustomer.publicId;
  expect(typeof customerId).toBe('string');
  expect(createdCustomer.displayName).toBe(displayName);
  expect(createdCustomer.customerMemo).toBe(customerMemo);
  await expect(page.locator('[data-customer-detail-page]')).toHaveAttribute(
    'data-selected-customer-id',
    customerId,
  );

  await page.locator('[data-edit-customer]').click();
  await expect(page.locator('[data-customer-edit-page]')).toHaveAttribute(
    'data-editing-customer-id',
    customerId,
  );
  await page.locator('[data-customer-field="displayName"]').fill(updatedDisplayName);
  await page.locator('[data-customer-field="customerMemo"]').fill(updatedCustomerMemo);
  const customerEditPromise = rpl64WaitForApiResponse(
    page,
    'PATCH',
    '/api/v1/users/customers/' + customerId,
  );
  await page.locator('[data-customer-form-submit]').click();
  const customerEditResponse = await customerEditPromise;
  expect(customerEditResponse.status()).toBe(200);
  const editedCustomer = await rpl64ResponseData(customerEditResponse, 'Updated customer');
  expect(editedCustomer.publicId).toBe(customerId);
  expect(editedCustomer.displayName).toBe(updatedDisplayName);
  expect(editedCustomer.customerMemo).toBe(updatedCustomerMemo);
  await expect(page.getByTestId('customer-memo-copy')).toHaveText(updatedCustomerMemo);

  await page.locator('[data-add-event]').click();
  await expect(page.locator('[data-event-form]')).toBeVisible();
  await page.locator('[data-event-field="descriptor"]').fill(descriptor);
  await page.locator('[data-event-field="scheduledAt"]').fill(scheduledAt);
  const eventCreatePromise = rpl64WaitForApiResponse(page, 'POST', '/api/v1/customer-events');
  await page.locator('[data-event-form-submit]').click();
  const eventCreateResponse = await eventCreatePromise;
  expect(eventCreateResponse.status()).toBeGreaterThanOrEqual(200);
  expect(eventCreateResponse.status()).toBeLessThan(300);
  const createdEvent = await rpl64ResponseData(eventCreateResponse, 'CustomerEvent');
  const eventId = createdEvent.id;
  expect(typeof eventId).toBe('string');
  expect(createdEvent.customerId).toBe(customerId);
  expect(createdEvent.status).toBe('PLANNED');
  await expect(page.locator('[data-upcoming-primary]')).toContainText(descriptor);
  await page.locator('[data-upcoming-primary]').click();
  await expect(page).toHaveURL('/app/events/' + eventId);
  await expect(page.locator('[data-event-detail]')).toHaveAttribute('data-event-status', 'PLANNED');

  await page.locator('[data-event-action="edit"]').click();
  await expect(page.locator('[data-event-form][data-event-form-mode="edit"]')).toBeVisible();
  await page.locator('[data-event-field="scheduledAt"]').fill(rescheduledAt);
  const eventEditPromise = rpl64WaitForApiResponse(
    page,
    'PATCH',
    '/api/v1/customer-events/' + eventId,
  );
  await page.locator('[data-event-form-submit]').click();
  const eventEditResponse = await eventEditPromise;
  expect(eventEditResponse.status()).toBe(200);
  const editedEvent = await rpl64ResponseData(eventEditResponse, 'Updated CustomerEvent');
  expect(editedEvent.id).toBe(eventId);
  expect(editedEvent.customerId).toBe(customerId);
  expect(editedEvent.status).toBe('PLANNED');
  expect(Date.parse(editedEvent.scheduledAt)).toBe(Date.parse(rescheduledAt + ':00+09:00'));

  await rpl64OpenCustomerDetail(page, customerId, updatedDisplayName);
  await expect(page.locator('[data-upcoming-primary]')).toContainText(descriptor);
  await page.locator('[data-upcoming-primary]').click();
  await expect(page).toHaveURL('/app/events/' + eventId);
  await page.locator('[data-event-action="occur"]').click();
  await expect(page.locator('[data-event-occurrence-confirmation]')).toBeVisible();
  await page.locator('[data-event-occurrence-time]').fill(occurredAt);
  const occurPromise = rpl64WaitForApiResponse(
    page,
    'POST',
    '/api/v1/customer-events/' + eventId + '/occur',
  );
  await page.locator('[data-event-occurrence-confirm]').click();
  const occurResponse = await occurPromise;
  expect(occurResponse.status()).toBe(200);
  const occurredEvent = await rpl64ResponseData(occurResponse, 'Occurred CustomerEvent');
  expect(occurredEvent.id).toBe(eventId);
  expect(occurredEvent.customerId).toBe(customerId);
  expect(occurredEvent.status).toBe('OCCURRED');
  expect(Date.parse(occurredEvent.scheduledAt)).toBe(Date.parse(rescheduledAt + ':00+09:00'));
  expect(Date.parse(occurredEvent.occurredAt)).toBe(Date.parse(occurredAt + ':00+09:00'));

  const otherPage = await browser.newPage();
  try {
    await rpl64RegisterSyntheticAuthority(otherPage, {
      organizationId: otherOrganizationId,
      accountId: otherAccountId,
      publicId: otherPublicId,
    });
    await otherPage.goto('/auth/login');
    await rpl64LoginThroughUi(otherPage, 'rpl64-synthetic-other-manager', 'synthetic-test-only');
    await otherPage.locator('[data-side-navigation] a[href="/app/customers"]').click();
    await expect(otherPage.locator('[data-customers-page]')).toBeVisible();
    await expect(otherPage.locator('[data-customers-empty]')).toBeVisible();
    await expect(
      otherPage.locator('[data-customer-card][data-customer-id="' + customerId + '"]'),
    ).toHaveCount(0);
  } finally {
    await otherPage.close();
  }

  const existingResult = JSON.parse(await readFile(resultFile, 'utf8'));
  existingResult.ui = {
    customerId,
    organizationId,
    displayName: updatedDisplayName,
    customerMemo: updatedCustomerMemo,
    eventId,
    eventStatus: occurredEvent.status,
    scheduledAt: occurredEvent.scheduledAt,
    occurredAt: occurredEvent.occurredAt,
  };
  await writeFile(resultFile, JSON.stringify(existingResult), { encoding: 'utf8' });
});
