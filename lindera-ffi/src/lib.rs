use std::ffi::{CStr, CString};
use std::os::raw::c_char;
use std::ptr;
use std::sync::Mutex;

use lindera::dictionary::load_dictionary;
use lindera::mode::Mode;
use lindera::segmenter::Segmenter;
use lindera::tokenizer::Tokenizer;
use serde::Serialize;

#[derive(Serialize)]
struct FuriganaToken {
    surface: String,
    reading: String,
    is_kanji: bool,
}

fn has_kanji(text: &str) -> bool {
    text.chars().any(|c| {
        matches!(c,
            '\u{4E00}'..='\u{9FFF}' |
            '\u{3400}'..='\u{4DBF}' |
            '\u{F900}'..='\u{FAFF}'
        )
    })
}

/// IPADIC misanalysis correction table.
///
/// IPADIC's Viterbi algorithm sometimes prefers Korean/Chinese person-name (人名)
/// readings over common-noun readings for everyday kanji. This is caused by
/// lower connection costs between person-name context IDs and particles
/// (が, は, を, etc.).
///
/// This function detects misanalyzed tokens by checking POS tags and overrides
/// the reading with the standard Japanese common-noun reading.
///
/// ## Correction Categories
///
/// ### Category 1: Korean Surname → Common Noun (confirmed IPADIC bugs)
///
/// | Surface | Bug Reading      | Correct Reading | Trigger          |
/// |---------|------------------|-----------------|------------------|
/// | 金      | キム (Kim)       | カネ (money)    | 金が → きむが    |
/// | 全      | チョン (Jeon)    | ゼン (all)      | 全が → ちょんが  |
/// | 朴      | ボク (Park)      | ホウ (magnolia) | 朴が → ぼくが    |
///
/// ### Category 2: Korean reading safety net (reading-specific)
///
/// | Surface | Korean Reading       | Correct Reading      |
/// |---------|----------------------|----------------------|
/// | 白      | ペク/パク (Baek)     | シロ (white)         |
/// | 南      | ナム (Nam)           | ミナミ (south)       |
/// | 林      | イム/リム (Lim)      | ハヤシ (forest)      |
/// | 文      | ムン (Moon)          | ブン (writing)       |
/// | 河      | ハ (Ha)              | カワ (river)         |
/// | 玉      | オク (Ok)            | タマ (ball/gem)      |
/// | 権      | クォン/クオン (Kwon) | ケン (authority)     |
/// | 宋      | ソン (Song)          | ソウ (Song dynasty)  |
///
/// ### Category 3: Standalone reading corrections (POS-independent)
///
/// | Surface | Wrong Reading | Correct Reading | Note                              |
/// |---------|---------------|-----------------|-----------------------------------|
/// | 後      | ゴ            | アト            | Compounds (後輩,後悔) unaffected  |
fn correct_misanalyzed_reading(surface: &str, details: &[&str], reading: &str) -> Option<&'static str> {
    // Check person-name POS: 名詞,固有名詞,人名,(姓|名)
    let is_person_name = details.len() > 3
        && details[0] == "名詞"
        && details[1] == "固有名詞"
        && details[2] == "人名";

    if is_person_name {
        return match surface {
            // --- Category 1: Confirmed IPADIC bugs — always correct ---
            // 金: person-name キム(7374) vs common キン(6317)/カネ(6576)
            "金" => Some("カネ"),
            // 全: person-name チョン(9875) vs common ゼン(9524)
            "全" => Some("ゼン"),
            // 朴: person-name ボク(7344) vs common ホウ(6288)
            "朴" => Some("ホウ"),

            // --- Category 2: Korean reading safety — correct only specific readings ---
            "白" if reading == "ペク" || reading == "パク" => Some("シロ"),
            "南" if reading == "ナム" => Some("ミナミ"),
            "林" if reading == "イム" || reading == "リム" => Some("ハヤシ"),
            "文" if reading == "ムン" => Some("ブン"),
            "河" if reading == "ハ" => Some("カワ"),
            "玉" if reading == "オク" => Some("タマ"),
            "権" if reading == "クォン" || reading == "クオン" => Some("ケン"),
            "宋" if reading == "ソン" => Some("ソウ"),

            _ => None,
        };
    }

    // --- Category 3: Standalone reading corrections ---
    // 後 with ゴ → アト (compounds like 後輩/後悔 are single tokens, unaffected)
    if surface == "後" && reading == "ゴ" {
        return Some("アト");
    }

    None
}

fn katakana_to_hiragana(text: &str) -> String {
    text.chars()
        .map(|c| {
            if ('\u{30A1}'..='\u{30F6}').contains(&c) {
                char::from_u32(c as u32 - 0x60).unwrap_or(c)
            } else {
                c
            }
        })
        .collect()
}

static TOKENIZER: Mutex<Option<Tokenizer>> = Mutex::new(None);

/// Initialize the Lindera tokenizer with the embedded IPADIC dictionary.
/// Returns 1 on success, 0 on failure.
#[no_mangle]
pub extern "C" fn lindera_init() -> i32 {
    let dictionary = match load_dictionary("embedded://ipadic") {
        Ok(d) => d,
        Err(_) => return 0,
    };
    let segmenter = Segmenter::new(Mode::Normal, dictionary, None);
    let tokenizer = Tokenizer::new(segmenter);
    let mut guard = TOKENIZER.lock().unwrap_or_else(|e| e.into_inner());
    *guard = Some(tokenizer);
    1
}

/// Tokenize Japanese text and return a JSON array of FuriganaToken.
/// The caller must free the returned string with `lindera_free`.
/// Returns null on error.
#[no_mangle]
pub extern "C" fn lindera_tokenize(text: *const c_char) -> *mut c_char {
    if text.is_null() {
        return ptr::null_mut();
    }

    let c_str = unsafe { CStr::from_ptr(text) };
    let input = match c_str.to_str() {
        Ok(s) => s,
        Err(_) => return ptr::null_mut(),
    };

    let guard = TOKENIZER.lock().unwrap_or_else(|e| e.into_inner());
    let tokenizer = match guard.as_ref() {
        Some(t) => t,
        None => return ptr::null_mut(),
    };

    let mut tokens = match tokenizer.tokenize(input) {
        Ok(t) => t,
        Err(_) => return ptr::null_mut(),
    };

    let furigana_tokens: Vec<FuriganaToken> = tokens
        .iter_mut()
        .map(|token| {
            let surface = token.surface.as_ref().to_string();
            // IPADIC detail fields:
            // [0]=品詞, [1]=品詞細分1, [2]=品詞細分2, [3]=品詞細分3,
            // [4]=活用型, [5]=活用形, [6]=原形, [7]=読み, [8]=発音
            let details = token.details();
            let reading = if details.len() > 7 {
                let raw_reading = details[7];
                let corrected = correct_misanalyzed_reading(&surface, &details, raw_reading);
                katakana_to_hiragana(corrected.unwrap_or(raw_reading))
            } else {
                surface.clone()
            };
            let is_kanji = has_kanji(&surface);
            FuriganaToken {
                surface,
                reading,
                is_kanji,
            }
        })
        .collect();

    match serde_json::to_string(&furigana_tokens) {
        Ok(json) => match CString::new(json) {
            Ok(c) => c.into_raw(),
            Err(_) => ptr::null_mut(),
        },
        Err(_) => ptr::null_mut(),
    }
}

/// Free a string returned by `lindera_tokenize`.
#[no_mangle]
pub extern "C" fn lindera_free(ptr: *mut c_char) {
    if !ptr.is_null() {
        unsafe {
            let _ = CString::from_raw(ptr);
        }
    }
}

/// Destroy the tokenizer and release resources.
#[no_mangle]
pub extern "C" fn lindera_destroy() {
    let mut guard = TOKENIZER.lock().unwrap_or_else(|e| e.into_inner());
    *guard = None;
}

#[cfg(feature = "android")]
mod android {
    use super::*;
    use jni::JNIEnv;
    use jni::objects::{JObject, JString};
    use jni::sys::{jint, jstring};

    #[no_mangle]
    pub extern "system" fn Java_com_manhwaviewer_data_furigana_LinderaFuriganaAnalyzer_nativeInit(
        _env: JNIEnv,
        _obj: JObject,
    ) -> jint {
        lindera_init()
    }

    #[no_mangle]
    pub extern "system" fn Java_com_manhwaviewer_data_furigana_LinderaFuriganaAnalyzer_nativeTokenize(
        mut env: JNIEnv,
        _obj: JObject,
        text: JString,
    ) -> jstring {
        let input: String = match env.get_string(&text) {
            Ok(s) => s.into(),
            Err(_) => return std::ptr::null_mut(),
        };
        let c_text = match CString::new(input) {
            Ok(c) => c,
            Err(_) => return std::ptr::null_mut(),
        };
        let result = lindera_tokenize(c_text.as_ptr());
        if result.is_null() {
            return std::ptr::null_mut();
        }
        let json_str = unsafe { CStr::from_ptr(result) };
        let output = env
            .new_string(json_str.to_str().unwrap_or("[]"))
            .unwrap_or_else(|_| env.new_string("[]").unwrap());
        unsafe { lindera_free(result) };
        output.into_raw()
    }

    #[no_mangle]
    pub extern "system" fn Java_com_manhwaviewer_data_furigana_LinderaFuriganaAnalyzer_nativeDestroy(
        _env: JNIEnv,
        _obj: JObject,
    ) {
        lindera_destroy();
    }
}
