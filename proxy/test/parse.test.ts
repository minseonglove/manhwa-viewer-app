import { describe, expect, it } from "vitest";
import { parseOutput } from "../src/parse";

describe("parseOutput", () => {
  it("reads the compact id->text map", () => {
    expect(parseOutput('{"t01":"가","t02":"나"}', ["t01", "t02"])).toEqual({ translations: { t01: "가", t02: "나" }, problems: [] });
  });
  it("reads the items-array form and strips a code fence", () => {
    expect(parseOutput('```json\n{"items":[{"id":"t01","ko":"가"}]}\n```', ["t01"]).translations).toEqual({ t01: "가" });
  });
  it("reports missing and unexpected ids instead of guessing", () => {
    const r = parseOutput('{"t01":"가","t09":"엉뚱"}', ["t01", "t02"]);
    expect(r.problems).toEqual(["missing ids: t02", "unexpected ids: t09"]);
  });
  it("reports empty and invalid responses", () => {
    expect(parseOutput("", ["t01"]).problems).toEqual(["empty response"]);
    expect(parseOutput("not json", ["t01"]).problems[0]).toMatch(/^invalid json/);
    expect(parseOutput('{"t01": 3}', ["t01"]).problems).toContain("t01: not a string");
  });
});
