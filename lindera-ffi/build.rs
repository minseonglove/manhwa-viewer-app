use std::fs;
use std::io::Cursor;
use std::path::{Path, PathBuf};

/// Dictionary files that lindera-ipadic produces during its build.
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

/// Find the lindera-ipadic dictionary directory by scanning the target build tree.
///
/// When lindera-ipadic (with embed-ipadic) is built as a dependency, its build.rs
/// downloads and compiles the IPADIC dictionary into:
///   target/<profile>/build/lindera-ipadic-<hash>/out/lindera-ipadic/
///
/// Our build.rs runs AFTER lindera-ipadic's build.rs, so those files already exist.
fn find_ipadic_dir() -> PathBuf {
    let out_dir = std::env::var("OUT_DIR").unwrap();
    let out_path = Path::new(&out_dir);

    // OUT_DIR is like: target/<profile>/build/lindera-ffi-<hash>/out
    // We need:         target/<profile>/build/lindera-ipadic-<hash>/out/lindera-ipadic
    let build_dir = out_path
        .parent() // lindera-ffi-<hash>
        .and_then(|p| p.parent()) // build/
        .expect("Cannot find build directory from OUT_DIR");

    // Scan for lindera-ipadic output directories
    let entries = fs::read_dir(build_dir).expect("Cannot read build directory");

    for entry in entries.flatten() {
        let name = entry.file_name();
        let name_str = name.to_string_lossy();
        if name_str.starts_with("lindera-ipadic-") {
            let dict_path = entry.path().join("out").join("lindera-ipadic");
            if dict_path.join("dict.da").exists() {
                return dict_path;
            }
        }
    }

    panic!(
        "Could not find lindera-ipadic dictionary in build directory: {}. \
         Make sure lindera is compiled with the embed-ipadic feature.",
        build_dir.display()
    );
}

fn main() {
    println!("cargo:rerun-if-changed=build.rs");

    let ipadic_dir = find_ipadic_dir();
    let out_dir = PathBuf::from(std::env::var("OUT_DIR").unwrap());
    let compressed_dir = out_dir.join("ipadic-zstd");
    fs::create_dir_all(&compressed_dir).expect("Failed to create compressed dir");

    eprintln!("lindera-ffi build.rs: Found IPADIC at {}", ipadic_dir.display());

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
