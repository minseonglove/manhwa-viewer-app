use std::env;
use std::fs;
use std::io::Cursor;
use std::path::{Path, PathBuf};

use lindera_dictionary::assets::{fetch, FetchParams};
use lindera_dictionary::builder::DictionaryBuilder;
use lindera_dictionary::dictionary::metadata::Metadata;

/// Dictionary files that the IPADIC build produces.
const DICT_FILES: &[&str] = &[
    "char_def.bin",
    "matrix.mtx",
    "dict.da",
    "dict.vals",
    "dict.wordsidx",
    "dict.words",
    "unk.bin",
    "metadata.json",
];

/// The dictionary is built here rather than read from lindera-ipadic's build output:
/// Cargo does not run a dependency's build script before ours unless it declares `links`,
/// so on a clean target lindera-ipadic's output may not exist yet when this script runs.
///
/// Source, checksum and `ipadic-metadata.json` are lindera-ipadic 3.0.5's; update them together
/// when upgrading lindera, since the runtime loader in `lindera-dictionary` must match the format.
fn ipadic_params() -> FetchParams {
    FetchParams {
        file_name: "mecab-ipadic-2.7.0-20250920.tar.gz",
        input_dir: "mecab-ipadic-2.7.0-20250920",
        src_subdir: None,
        output_dir: "lindera-ipadic",
        dummy_input: "テスト,1288,1288,-1000,名詞,固有名詞,一般,*,*,*,*,*,*\n",
        download_urls: &["https://Lindera.dev/mecab-ipadic-2.7.0-20250920.tar.gz"],
        md5_hash: "a95c409f12f1023fce8ef91f991ef042",
    }
}

fn build_ipadic(out_dir: &Path) -> PathBuf {
    // With either variable set, `fetch` builds into that cache instead of OUT_DIR,
    // and the compression step below reads OUT_DIR.
    env::remove_var("LINDERA_DICTIONARIES_PATH");
    env::remove_var("LINDERA_CACHE");

    let metadata: Metadata = serde_json::from_str(include_str!("ipadic-metadata.json"))
        .expect("Failed to parse ipadic-metadata.json");

    tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .expect("Failed to start tokio runtime")
        .block_on(fetch(ipadic_params(), DictionaryBuilder::new(metadata)))
        .expect("Failed to download and build the IPADIC dictionary");

    out_dir.join(ipadic_params().output_dir)
}

fn main() {
    println!("cargo:rerun-if-changed=build.rs");
    println!("cargo:rerun-if-changed=ipadic-metadata.json");

    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());
    let ipadic_dir = build_ipadic(&out_dir);
    let compressed_dir = out_dir.join("ipadic-zstd");
    fs::create_dir_all(&compressed_dir).expect("Failed to create compressed dir");

    eprintln!("lindera-ffi build.rs: Built IPADIC at {}", ipadic_dir.display());

    // Compress each dictionary file with zstd level 19
    for &filename in DICT_FILES {
        let src = ipadic_dir.join(filename);
        let dst = compressed_dir.join(format!("{}.zst", filename));

        let data = fs::read(&src).unwrap_or_else(|e| {
            panic!("Failed to read {}: {}", src.display(), e);
        });

        let compressed = zstd::encode_all(Cursor::new(&data), 19).unwrap_or_else(|e| {
            panic!("Failed to compress {}: {}", filename, e);
        });

        let ratio = compressed.len() as f64 / data.len() as f64 * 100.0;
        eprintln!(
            "  {} -> {}.zst: {} -> {} ({:.1}%)",
            filename,
            filename,
            data.len(),
            compressed.len(),
            ratio
        );

        fs::write(&dst, &compressed).unwrap_or_else(|e| {
            panic!("Failed to write {}: {}", dst.display(), e);
        });
    }

    // Export the compressed directory path for use in lib.rs via include_bytes!
    println!("cargo:rustc-env=IPADIC_ZSTD_DIR={}", compressed_dir.display());

    eprintln!("lindera-ffi build.rs: Compressed dictionary written to {}", compressed_dir.display());
}
