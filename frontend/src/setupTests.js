// jest-dom adds custom jest matchers for asserting on DOM nodes.
// allows you to do things like:
// expect(element).toHaveTextContent(/react/i)
// learn more: https://github.com/testing-library/jest-dom
import '@testing-library/jest-dom';

// jsdom (as bundled by react-scripts 5 / Jest 27) has no built-in fetch - this polyfills
// fetch/Request/Response/Headers via XMLHttpRequest, which MSW's Node XHR interceptor can see.
import 'whatwg-fetch';

import { server } from './mocks/server';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
