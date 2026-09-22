// Shared by the Node-side outbound-service hook (vitest.workerd.config.ts) and the workerd test
// (google-oauth.test.ts) so both sides agree on the token without the test importing the config.
export const MOCK_GOOGLE_ACCESS_TOKEN = "workerd-mock-access-token";
