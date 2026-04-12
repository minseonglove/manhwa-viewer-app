//! Integration tests for lindera-ffi FFI functions.
//!
//! IMPORTANT: These tests use a global static TOKENIZER and must be run
//! with `cargo test -- --test-threads=1` to avoid race conditions.

use std::ffi::{CStr, CString};

use lindera_ffi::{lindera_destroy, lindera_free, lindera_init, lindera_tokenize};

fn setup() {
    let result = lindera_init();
    assert_eq!(result, 1, "lindera_init failed");
}

fn teardown() {
    lindera_destroy();
}

fn tokenize(text: &str) -> Vec<serde_json::Value> {
    let c_text = CString::new(text).unwrap();
    let result = lindera_tokenize(c_text.as_ptr());
    assert!(!result.is_null(), "tokenize returned null for: {}", text);
    let json_str = unsafe { CStr::from_ptr(result) }
        .to_str()
        .unwrap()
        .to_string();
    lindera_free(result);
    serde_json::from_str(&json_str).unwrap()
}

#[test]
fn test_init() {
    setup();
    teardown();
}

#[test]
fn test_compound_word_kyou() {
    setup();
    let tokens = tokenize("今日");
    let surfaces: String = tokens
        .iter()
        .map(|t| t["surface"].as_str().unwrap())
        .collect();
    assert_eq!(surfaces, "今日");

    let has_kanji_token = tokens.iter().any(|t| t["is_kanji"].as_bool().unwrap());
    assert!(has_kanji_token, "今日 should contain kanji tokens");

    let reading: String = tokens
        .iter()
        .map(|t| t["reading"].as_str().unwrap())
        .collect();
    assert_eq!(reading, "きょう", "今日 reading should be きょう");
    teardown();
}

#[test]
fn test_compound_word_taberu() {
    setup();
    let tokens = tokenize("食べる");
    let surfaces: String = tokens
        .iter()
        .map(|t| t["surface"].as_str().unwrap())
        .collect();
    assert_eq!(surfaces, "食べる");

    let first_kanji = tokens.iter().find(|t| {
        let s = t["surface"].as_str().unwrap();
        s.contains('食')
    });
    assert!(first_kanji.is_some(), "Should have a token containing 食");
    assert!(
        first_kanji.unwrap()["is_kanji"].as_bool().unwrap(),
        "Token with 食 should have is_kanji=true"
    );
    teardown();
}

#[test]
fn test_pure_hiragana_no_kanji() {
    setup();
    let tokens = tokenize("おはよう");
    assert!(
        !tokens.is_empty(),
        "Tokens should not be empty for おはよう"
    );
    for token in &tokens {
        assert!(
            !token["is_kanji"].as_bool().unwrap(),
            "Pure hiragana token '{}' should have is_kanji=false",
            token["surface"].as_str().unwrap()
        );
    }
    teardown();
}

#[test]
fn test_katakana_no_kanji() {
    setup();
    let tokens = tokenize("タワー");
    assert!(!tokens.is_empty(), "Tokens should not be empty for タワー");
    for token in &tokens {
        assert!(
            !token["is_kanji"].as_bool().unwrap(),
            "Katakana token '{}' should have is_kanji=false",
            token["surface"].as_str().unwrap()
        );
    }
    teardown();
}

#[test]
fn test_mixed_sentence() {
    setup();
    let tokens = tokenize("今日は天気がいいですね");
    let surfaces: String = tokens
        .iter()
        .map(|t| t["surface"].as_str().unwrap())
        .collect();
    assert_eq!(
        surfaces, "今日は天気がいいですね",
        "Surface concatenation should equal original"
    );

    let kanji_surfaces: Vec<&str> = tokens
        .iter()
        .filter(|t| t["is_kanji"].as_bool().unwrap())
        .map(|t| t["surface"].as_str().unwrap())
        .collect();
    assert!(
        !kanji_surfaces.is_empty(),
        "Should have at least one kanji token"
    );

    let non_kanji: Vec<&str> = tokens
        .iter()
        .filter(|t| !t["is_kanji"].as_bool().unwrap())
        .map(|t| t["surface"].as_str().unwrap())
        .collect();
    assert!(
        !non_kanji.is_empty(),
        "Should have non-kanji tokens (particles, etc.)"
    );
    teardown();
}

#[test]
fn test_manga_dialogue() {
    setup();
    let tokens = tokenize("俺は絶対に負けない！");
    let surfaces: String = tokens
        .iter()
        .map(|t| t["surface"].as_str().unwrap())
        .collect();
    assert_eq!(
        surfaces, "俺は絶対に負けない！",
        "Surface concatenation should match input"
    );

    let ore_token = tokens
        .iter()
        .find(|t| t["surface"].as_str().unwrap() == "俺");
    assert!(ore_token.is_some(), "Should find 俺 token");
    assert!(
        ore_token.unwrap()["is_kanji"].as_bool().unwrap(),
        "俺 should have is_kanji=true"
    );
    teardown();
}

#[test]
fn test_empty_string() {
    setup();
    let tokens = tokenize("");
    assert!(tokens.is_empty(), "Empty string should produce empty tokens");
    teardown();
}

#[test]
fn test_numbers_and_symbols() {
    setup();
    let tokens = tokenize("3人");
    let surfaces: String = tokens
        .iter()
        .map(|t| t["surface"].as_str().unwrap())
        .collect();
    assert_eq!(surfaces, "3人", "Surface concat should be 3人");

    let jin_token = tokens
        .iter()
        .find(|t| t["surface"].as_str().unwrap().contains('人'));
    assert!(jin_token.is_some(), "Should find a token containing 人");
    assert!(
        jin_token.unwrap()["is_kanji"].as_bool().unwrap(),
        "Token with 人 should have is_kanji=true"
    );
    teardown();
}

#[test]
fn test_null_input() {
    setup();
    let result = lindera_tokenize(std::ptr::null());
    assert!(result.is_null(), "Null input should return null");
    teardown();
}

#[test]
fn test_reading_is_hiragana_not_katakana() {
    setup();
    let tokens = tokenize("東京");
    let readings: String = tokens
        .iter()
        .map(|t| t["reading"].as_str().unwrap())
        .collect();

    let has_katakana = readings.chars().any(|c| ('\u{30A1}'..='\u{30F6}').contains(&c));
    assert!(
        !has_katakana,
        "Reading '{}' should be hiragana, not katakana",
        readings
    );

    let has_hiragana = readings
        .chars()
        .any(|c| ('\u{3041}'..='\u{3096}').contains(&c));
    assert!(
        has_hiragana,
        "Reading '{}' should contain hiragana characters",
        readings
    );

    assert_eq!(readings, "とうきょう", "東京 reading should be とうきょう");
    teardown();
}
