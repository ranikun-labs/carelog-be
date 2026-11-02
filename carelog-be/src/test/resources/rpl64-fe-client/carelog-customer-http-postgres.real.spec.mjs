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
