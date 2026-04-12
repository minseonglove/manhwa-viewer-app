use std::ffi::{CStr, CString};
use std::io::Cursor;
use std::os::raw::c_char;
use std::ptr;
use std::sync::Mutex;

use lindera::mode::Mode;
use lindera::segmenter::Segmenter;
use lindera::tokenizer::Tokenizer;
use lindera_dictionary::dictionary::Dictionary;
use lindera_dictionary::dictionary::character_definition::CharacterDefinition;
use lindera_dictionary::dictionary::connection_cost_matrix::ConnectionCostMatrix;
use lindera_dictionary::dictionary::metadata::Metadata;
use lindera_dictionary::dictionary::prefix_dictionary::PrefixDictionary;
use lindera_dictionary::dictionary::unknown_dictionary::UnknownDictionary;
use serde::Serialize;

// Embed zstd-compressed IPADIC dictionary files (compressed at build time by build.rs)
static CHAR_DEF_ZST: &[u8] = include_bytes!(concat!(env!("IPADIC_ZSTD_DIR"), "/char_def.bin.zst"));
static MATRIX_ZST: &[u8] = include_bytes!(concat!(env!("IPADIC_ZSTD_DIR"), "/matrix.mtx.zst"));
static DA_ZST: &[u8] = include_bytes!(concat!(env!("IPADIC_ZSTD_DIR"), "/dict.da.zst"));
static VALS_ZST: &[u8] = include_bytes!(concat!(env!("IPADIC_ZSTD_DIR"), "/dict.vals.zst"));
static WORDS_IDX_ZST: &[u8] = include_bytes!(concat!(env!("IPADIC_ZSTD_DIR"), "/dict.wordsidx.zst"));
static WORDS_ZST: &[u8] = include_bytes!(concat!(env!("IPADIC_ZSTD_DIR"), "/dict.words.zst"));
static UNK_ZST: &[u8] = include_bytes!(concat!(env!("IPADIC_ZSTD_DIR"), "/unk.bin.zst"));
static METADATA_ZST: &[u8] = include_bytes!(concat!(env!("IPADIC_ZSTD_DIR"), "/metadata.json.zst"));

/// Decompress a zstd-compressed byte slice.
fn decompress(data: &[u8]) -> Vec<u8> {
    zstd::decode_all(Cursor::new(data)).expect("Failed to decompress zstd data")
}

/// Load the IPADIC dictionary from zstd-compressed embedded data.
fn load_compressed_dictionary() -> Result<Dictionary, String> {
    let metadata_bytes = decompress(METADATA_ZST);
    let metadata = Metadata::load(&metadata_bytes).map_err(|e| format!("metadata: {e}"))?;

    let da_bytes = decompress(DA_ZST);
    let vals_bytes = decompress(VALS_ZST);
    let words_idx_bytes = decompress(WORDS_IDX_ZST);
    let words_bytes = decompress(WORDS_ZST);
    let prefix_dictionary =
        PrefixDictionary::load(da_bytes, vals_bytes, words_idx_bytes, words_bytes, true)
            .map_err(|e| format!("prefix_dict: {e}"))?;

    let matrix_bytes = decompress(MATRIX_ZST);
    let connection_cost_matrix =
        ConnectionCostMatrix::load(matrix_bytes).map_err(|e| format!("matrix: {e}"))?;

    let char_def_bytes = decompress(CHAR_DEF_ZST);
    let character_definition =
        CharacterDefinition::load(&char_def_bytes).map_err(|e| format!("char_def: {e}"))?;

    let unk_bytes = decompress(UNK_ZST);
    let unknown_dictionary =
        UnknownDictionary::load(&unk_bytes).map_err(|e| format!("unk: {e}"))?;

    Ok(Dictionary {
        prefix_dictionary,
        connection_cost_matrix,
        character_definition,
        unknown_dictionary,
        metadata,
    })
}

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
/// The dictionary is stored zstd-compressed and decompressed at runtime.
/// Returns 1 on success, 0 on failure.
#[no_mangle]
pub extern "C" fn lindera_init() -> i32 {
    let dictionary = match load_compressed_dictionary() {
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
                katakana_to_hiragana(details[7])
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
