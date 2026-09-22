import { cloudflareTest } from "@cloudflare/vitest-pool-workers";
import { defineConfig } from "vitest/config";
import { MOCK_GOOGLE_ACCESS_TOKEN } from "./test-workerd/google-oauth-mock.ts";

export default defineConfig({
  test: {
    include: ["test-workerd/**/*.test.ts"],
  },
  plugins: [
    cloudflareTest({
      wrangler: { configPath: "./test-workerd/wrangler.toml" },
      miniflare: {
        // Runs on the Node side and receives every fetch() the worker under test makes to an external origin,
        // so GoogleAccessTokens can be exercised against workerd's real global fetch without reaching the network.
        outboundService: (request) => {
          const url = new URL(request.url);
          if (url.host === "oauth2.googleapis.com") {
            return new Response(JSON.stringify({ access_token: MOCK_GOOGLE_ACCESS_TOKEN, expires_in: 3600 }), { headers: { "content-type": "application/json" } });
          }
          return new Response("unexpected outbound request in test", { status: 599 });
        },
      },
    }),
  ],
});
