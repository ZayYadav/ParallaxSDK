#include "ProcessBindingGuard.h"

#include <jni.h>

#include <algorithm>
#include <cctype>
#include <cerrno>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <fstream>
#include <set>
#include <string>
#include <vector>

#include <dlfcn.h>
#include <fcntl.h>
#include <limits.h>
#include <sys/stat.h>
#include <unistd.h>

#include <obfuscate.h>

namespace {
constexpr uint32_t ZIP_EOCD_MAGIC = 0x06054b50u;
constexpr uint32_t ZIP_CENTRAL_MAGIC = 0x02014b50u;
constexpr size_t ZIP_EOCD_MIN = 22u;
constexpr size_t ZIP_EOCD_SEARCH = ZIP_EOCD_MIN + 65535u;
constexpr size_t ZIP_CENTRAL_FIXED = 46u;

uint16_t le16(const unsigned char *p) {
    return static_cast<uint16_t>(p[0])
            | (static_cast<uint16_t>(p[1]) << 8u);
}

uint32_t le32(const unsigned char *p) {
    return static_cast<uint32_t>(p[0])
            | (static_cast<uint32_t>(p[1]) << 8u)
            | (static_cast<uint32_t>(p[2]) << 16u)
            | (static_cast<uint32_t>(p[3]) << 24u);
}

bool read_fully(int fd, off_t offset, void *buffer, size_t length) {
    auto *out = static_cast<unsigned char *>(buffer);
    size_t done = 0;
    while (done < length) {
        ssize_t count = pread(
                fd,
                out + done,
                length - done,
                offset + static_cast<off_t>(done));
        if (count < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        if (count == 0) return false;
        done += static_cast<size_t>(count);
    }
    return true;
}

std::string lower_copy(std::string value) {
    for (char &ch : value) {
        ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
    }
    return value;
}

bool ends_with(const std::string &value, const std::string &suffix) {
    return value.size() >= suffix.size()
            && value.compare(value.size() - suffix.size(), suffix.size(), suffix) == 0;
}

bool canonicalize(const std::string &path, std::string &out) {
    if (path.empty() || path[0] != '/') return false;
    char resolved[PATH_MAX];
    if (realpath(path.c_str(), resolved) == nullptr) return false;
    out.assign(resolved);
    return !out.empty() && out[0] == '/';
}

bool package_ok(const char *package_name) {
    return package_name != nullptr
            && std::strcmp(package_name, OBFUSCATE("com.onecore.loader")) == 0;
}

bool self_library_path(std::string &library_path) {
    Dl_info info{};
    if (dladdr(reinterpret_cast<void *>(&self_library_path), &info) == 0
            || info.dli_fname == nullptr) {
        return false;
    }
    std::string raw(info.dli_fname);
    if (raw.find('!') != std::string::npos || !ends_with(raw, "/libParallaxLoader.so")) {
        return false;
    }
    return canonicalize(raw, library_path)
            && ends_with(library_path, "/libParallaxLoader.so");
}

bool derive_runtime_base_apk(std::string &base_apk, std::string &library_path) {
    if (!self_library_path(library_path)) return false;

    const std::string marker = "/lib/arm64/libParallaxLoader.so";
    size_t marker_pos = library_path.rfind(marker);
    if (marker_pos == std::string::npos || marker_pos == 0u) return false;

    std::string install_root = library_path.substr(0, marker_pos);
    if (!(install_root.rfind("/data/app/", 0) == 0
            || install_root.rfind("/mnt/expand/", 0) == 0)) {
        return false;
    }

    std::string candidate = install_root + "/base.apk";
    if (!canonicalize(candidate, base_apk)) return false;
    if (base_apk != candidate || !ends_with(base_apk, "/base.apk")) return false;

    struct stat st{};
    if (lstat(base_apk.c_str(), &st) != 0 || !S_ISREG(st.st_mode)) return false;
    if ((st.st_mode & (S_IWGRP | S_IWOTH)) != 0) return false;
    if (access(base_apk.c_str(), W_OK) == 0) return false;
    return true;
}

bool same_file(const std::string &first, const std::string &second) {
    struct stat a{};
    struct stat b{};
    return stat(first.c_str(), &a) == 0
            && stat(second.c_str(), &b) == 0
            && S_ISREG(a.st_mode)
            && S_ISREG(b.st_mode)
            && a.st_dev == b.st_dev
            && a.st_ino == b.st_ino
            && a.st_size == b.st_size;
}

bool process_name_matches() {
    std::ifstream input("/proc/self/cmdline", std::ios::binary);
    if (!input.is_open()) return false;
    std::string cmdline;
    std::getline(input, cmdline, '\0');
    const std::string expected = OBFUSCATE("com.onecore.loader");
    return cmdline == expected || cmdline.rfind(expected + ":", 0) == 0;
}

bool writable_code_path(const std::string &path) {
    const std::string lowered = lower_copy(path);
    const bool code_suffix = ends_with(lowered, ".apk")
            || ends_with(lowered, ".dex")
            || ends_with(lowered, ".jar")
            || ends_with(lowered, ".odex")
            || ends_with(lowered, ".vdex");
    if (!code_suffix) return false;

    return lowered.rfind("/data/user/0/com.onecore.loader/", 0) == 0
            || lowered.rfind("/data/data/com.onecore.loader/", 0) == 0
            || lowered.rfind("/data/local/tmp/", 0) == 0
            || lowered.rfind("/sdcard/", 0) == 0
            || lowered.rfind("/storage/", 0) == 0;
}

bool maps_are_bound_to_self(
        const std::string &library_path,
        const std::string &base_apk,
        bool strict_runtime_scan) {
    std::ifstream maps("/proc/self/maps");
    if (!maps.is_open()) return false;

    std::set<std::string> loader_paths;
    std::string line;
    while (std::getline(maps, line)) {
        size_t path_pos = line.find('/');
        if (path_pos == std::string::npos) continue;
        std::string path = line.substr(path_pos);
        size_t deleted = path.find(" (deleted)");
        if (deleted != std::string::npos) path.erase(deleted);

        if (path.find("libParallaxLoader.so") != std::string::npos) {
            std::string canonical;
            if (!canonicalize(path, canonical)) return false;
            loader_paths.insert(canonical);
        }

        if (strict_runtime_scan && writable_code_path(path)) {
            std::string canonical;
            if (!canonicalize(path, canonical)) return false;
            if (canonical != base_apk) return false;
        }
    }

    return loader_paths.size() == 1u && *loader_paths.begin() == library_path;
}

bool suspicious_open_code_descriptors(const std::string &base_apk) {
    DIR *dir = opendir("/proc/self/fd");
    if (dir == nullptr) return true;

    bool suspicious = false;
    dirent *entry = nullptr;
    while ((entry = readdir(dir)) != nullptr && !suspicious) {
        if (!std::isdigit(static_cast<unsigned char>(entry->d_name[0]))) continue;
        std::string fd_path = std::string("/proc/self/fd/") + entry->d_name;
        char target[PATH_MAX];
        ssize_t count = readlink(fd_path.c_str(), target, sizeof(target) - 1u);
        if (count <= 0) continue;
        target[count] = '\0';
        std::string path(target);
        size_t deleted = path.find(" (deleted)");
        if (deleted != std::string::npos) path.erase(deleted);
        if (!writable_code_path(path)) continue;
        std::string canonical;
        if (!canonicalize(path, canonical) || canonical != base_apk) suspicious = true;
    }
    closedir(dir);
    return suspicious;
}

bool suspicious_nested_payloads(const std::string &base_apk) {
    int fd = open(base_apk.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return true;
    struct stat st{};
    if (fstat(fd, &st) != 0 || st.st_size < static_cast<off_t>(ZIP_EOCD_MIN)) {
        close(fd);
        return true;
    }

    const uint64_t file_size = static_cast<uint64_t>(st.st_size);
    const size_t search = static_cast<size_t>(
            std::min<uint64_t>(file_size, static_cast<uint64_t>(ZIP_EOCD_SEARCH)));
    const off_t tail_offset = st.st_size - static_cast<off_t>(search);
    std::vector<unsigned char> tail(search);
    if (!read_fully(fd, tail_offset, tail.data(), tail.size())) {
        close(fd);
        return true;
    }

    uint32_t central_offset = 0;
    uint32_t central_size = 0;
    bool found = false;
    for (size_t i = search - ZIP_EOCD_MIN + 1u; i-- > 0u;) {
        if (le32(tail.data() + i) != ZIP_EOCD_MAGIC) continue;
        const uint16_t comment_len = le16(tail.data() + i + 20u);
        if (i + ZIP_EOCD_MIN + comment_len != search) continue;
        central_size = le32(tail.data() + i + 12u);
        central_offset = le32(tail.data() + i + 16u);
        found = true;
        break;
    }
    if (!found
            || static_cast<uint64_t>(central_offset) + central_size > file_size) {
        close(fd);
        return true;
    }

    off_t cursor = static_cast<off_t>(central_offset);
    const off_t end = cursor + static_cast<off_t>(central_size);
    bool bad = false;
    while (cursor < end) {
        unsigned char fixed[ZIP_CENTRAL_FIXED];
        if (!read_fully(fd, cursor, fixed, sizeof(fixed))
                || le32(fixed) != ZIP_CENTRAL_MAGIC) {
            bad = true;
            break;
        }
        const uint16_t name_len = le16(fixed + 28u);
        const uint16_t extra_len = le16(fixed + 30u);
        const uint16_t comment_len = le16(fixed + 32u);
        if (name_len == 0u || name_len > 4096u) {
            bad = true;
            break;
        }

        std::vector<char> name(name_len);
        if (!read_fully(
                fd,
                cursor + static_cast<off_t>(ZIP_CENTRAL_FIXED),
                name.data(),
                name.size())) {
            bad = true;
            break;
        }

        const std::string lowered = lower_copy(std::string(name.begin(), name.end()));
        const bool payload_area = lowered.rfind("assets/", 0) == 0
                || lowered.rfind("res/raw/", 0) == 0;
        const bool executable_payload = ends_with(lowered, ".apk")
                || ends_with(lowered, ".dex")
                || ends_with(lowered, ".jar")
                || ends_with(lowered, ".odex")
                || ends_with(lowered, ".vdex");
        const bool obvious_wrapper_name = lowered.find("original.apk") != std::string::npos
                || lowered.find("original_app") != std::string::npos
                || lowered.find("backup.apk") != std::string::npos;
        if ((payload_area && executable_payload) || obvious_wrapper_name) {
            bad = true;
            break;
        }

        const uint64_t advance = ZIP_CENTRAL_FIXED
                + static_cast<uint64_t>(name_len)
                + extra_len
                + comment_len;
        if (advance > static_cast<uint64_t>(end - cursor)) {
            bad = true;
            break;
        }
        cursor += static_cast<off_t>(advance);
    }

    close(fd);
    return bad || cursor != end;
}
} // namespace

bool onecore_verify_process_bound_apk(
        const char *claimed_apk_path,
        const char *package_name,
        bool strict_runtime_scan) {
    if (!package_ok(package_name) || !process_name_matches()) return false;

    std::string base_apk;
    std::string library_path;
    if (!derive_runtime_base_apk(base_apk, library_path)) return false;

    if (claimed_apk_path == nullptr || claimed_apk_path[0] == '\0') return false;
    std::string claimed;
    if (!canonicalize(claimed_apk_path, claimed)
            || claimed != base_apk
            || !same_file(claimed, base_apk)) {
        return false;
    }

    if (!maps_are_bound_to_self(library_path, base_apk, strict_runtime_scan)) return false;
    if (strict_runtime_scan && suspicious_open_code_descriptors(base_apk)) return false;
    if (suspicious_nested_payloads(base_apk)) return false;

    // This function owns process/file binding only. Do not mix runtime relocated native text
    // bytes into APK signing identity: Android's loader may legitimately alter runtime mapping
    // state even though the installed base.apk and signing certificate are authentic. Signer and
    // signed-content validation is performed independently by AppIntegrity/apksig plus the native
    // expected-certificate digest comparison.
    return maps_are_bound_to_self(library_path, base_apk, strict_runtime_scan);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onecore_loader_security_NativeSigningVerifier_verifyProcessBoundApkNative(
        JNIEnv *env,
        jclass,
        jstring apkPath,
        jstring packageName) {
    if (apkPath == nullptr || packageName == nullptr) return JNI_FALSE;
    const char *apk = env->GetStringUTFChars(apkPath, nullptr);
    const char *pkg = env->GetStringUTFChars(packageName, nullptr);
    if (apk == nullptr || pkg == nullptr) {
        if (apk != nullptr) env->ReleaseStringUTFChars(apkPath, apk);
        if (pkg != nullptr) env->ReleaseStringUTFChars(packageName, pkg);
        return JNI_FALSE;
    }

    const bool ok = onecore_verify_process_bound_apk(apk, pkg, false);
    env->ReleaseStringUTFChars(apkPath, apk);
    env->ReleaseStringUTFChars(packageName, pkg);
    return ok ? JNI_TRUE : JNI_FALSE;
}
